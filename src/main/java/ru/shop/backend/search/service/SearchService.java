package ru.shop.backend.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.shop.backend.search.model.*;
import ru.shop.backend.search.repository.ItemDbRepository;
import ru.shop.backend.search.repository.ItemRepository;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {
    private final ItemRepository repo;
    private final ItemDbRepository repoDb;

    private final Pageable pageable = PageRequest.of(0, 150);
    private final Pageable pageableSmall = PageRequest.of(0, 10);
    private static final Pattern pattern = Pattern.compile("\\d+");
    private static final Map<Character, Character> cyrillicToLatinMap;
    private static final Map<Character, Character> latinToCyrillicMap;

    static {
        cyrillicToLatinMap = new HashMap<>();
        latinToCyrillicMap = new HashMap<>();

        char[] ru = {'й', 'ц', 'у', 'к', 'е', 'н', 'г', 'ш', 'щ', 'з', 'х', 'ъ', 'ф', 'ы', 'в', 'а', 'п', 'р', 'о', 'л', 'д', 'ж', 'э', 'я', 'ч', 'с', 'м', 'и', 'т', 'ь', 'б', 'ю', '.',
                ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-'};
        char[] en = {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p', '[', ']', 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', ';', '"', 'z', 'x', 'c', 'v', 'b', 'n', 'm', ',', '.', '/',
                ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-'};

        for (int i = 0; i < ru.length; i++) {
            cyrillicToLatinMap.put(ru[i], en[i]);
            latinToCyrillicMap.put(en[i], ru[i]);
        }
    }

    public synchronized SearchResult getSearchResult(Integer regionId, String text) {
        List<CatalogueElastic> result = getResultFromTextSmall(text);
        List<Item> items = getItemsByResult(regionId, result);
        String brand = extractBrandFromResult(result);
        List<Category> categories = getCategoriesByItems(items, brand);
        List<TypeHelpText> helpText = result.isEmpty() ? new ArrayList<>() :
                List.of(new TypeHelpText(TypeOfQuery.SEE_ALSO,
                        ((result.get(0).getItems().isEmpty() ? "" : result.get(0).getItems().get(0).getType()) +
                                " " + brand).trim()));
        return new SearchResult(items, categories, helpText);
    }

    private List<CatalogueElastic> getResultFromText(String text, Pageable pageableRequest) {
        if (!isNumeric(text)) {
            if (pageableRequest.equals(pageable))
                return getAll(text, pageable);
            else
                return getAll(text, pageableSmall);
        }

        Integer itemId = repoDb.findBySku(text).stream().findFirst().orElse(null);

        if (itemId != null) {
            try {
                return getByItemId(itemId.toString());
            } catch (Exception e) {
                log.error(e.getLocalizedMessage());
            }
        }

        List<CatalogueElastic> catalogue = getByName(text);
        if (!catalogue.isEmpty()) {
            return catalogue;
        }

        return new ArrayList<>();
    }

    private String extractBrandFromResult(List<CatalogueElastic> result) {
        if (result.isEmpty()) {
            return "";
        }
        log.error(result.get(0).toString()+"!!!");
        return result.stream()
                .findFirst()
                .map(category -> {
                    String brand = category.getBrand();
                    return brand != null ? brand.toLowerCase(Locale.ROOT) : "";
                })
                .orElse("");
    }

    private List<Item> getItemsByResult(Integer regionId, List<CatalogueElastic> result) {
        List<ItemElastic> itemElastics = result.stream()
                .flatMap(category -> category.getItems().stream())
                .collect(Collectors.toList());

        List<Object[]> itemObjects = repoDb.findByIds(regionId, itemElastics.stream()
                .map(ItemElastic::getItemId)
                .collect(Collectors.toList()));

        return itemObjects.stream()
                .map(this::mapToObjectItem)
                .collect(Collectors.toList());
    }

    private Item mapToObjectItem(Object[] arr) {
        return new Item(
                ((BigInteger) arr[2]).intValue(),
                arr[1].toString(),
                arr[3].toString(),
                arr[4].toString(),
                ((BigInteger) arr[0]).intValue(),
                arr[5].toString());
    }

    private List<Category> getCategoriesByItems(List<Item> items, String brand) {
        Set<String> catUrls = new HashSet<>();
        return repoDb.findCatsByIds(items.stream()
                        .map(Item::getItemId)
                        .collect(Collectors.toList()))
                .stream().map(arr -> {
                    String catUrl = arr[2].toString();
                    if (catUrls.contains(catUrl)) {
                        return null;
                    }
                    catUrls.add(catUrl);
                    return new Category(
                            arr[0].toString(),
                            arr[1].toString(),
                            "/cat/" + catUrl + (brand.isEmpty() ? "" : "/brands/" + brand),
                            "/cat/" + arr[3].toString(),
                            arr[4] == null ? null : arr[4].toString()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        return pattern.matcher(strNum).matches();
    }

    public synchronized List<CatalogueElastic> getResultFromTextSmall(String text) {
        return getResultFromText(text, pageableSmall);
    }

    public List<CatalogueElastic> getResultFromTextFull(String text) {
        return getResultFromText(text, pageable);
    }

    public List<CatalogueElastic> getAll(String text, Pageable pageable) {

        String type = "";
        String brand = "";
        String text2 = text;
        Long catalogueId = null;
        boolean needConvert = true;
        List<ItemElastic> list = new ArrayList<>();

        if(isContainErrorChar(text)) {
            text = convert(text);
            needConvert = false;
        }
        if(needConvert && isContainErrorChar(convert(text))) {
            needConvert = false;
        }

        if (text.contains(" "))
            for (String queryWord : text.split("\\s")) {
                list = repo.findAllByBrand(queryWord, pageable);
                if (list.isEmpty() && needConvert) {
                    list = repo.findAllByBrand(convert(text), pageable);
                }
                if (!list.isEmpty()) {
                    text = text.replace(queryWord, "").trim().replace("  ", " ");
                    brand = list.get(0).getBrand();
                    break;
                }
            }
        list = repo.findAllByType(text, pageable);
        if (list.isEmpty() && needConvert) {
            list = repo.findAllByType(convert(text), pageable);
        }
        if (!list.isEmpty()) {
            type = (list.stream().map(ItemElastic::getType).min(Comparator.comparingInt(String::length)).get());
        } else {
            for (String queryWord : text.split("\\s")) {
                list = repo.findAllByType(queryWord, pageable);
                if (list.isEmpty() && needConvert) {
                    list = repo.findAllByType(convert(text), pageable);
                }
                if (!list.isEmpty()) {
                    text = text.replace(queryWord, "");
                    type = (list.stream().map(ItemElastic::getType).min(Comparator.comparingInt(String::length)).get());
                }
            }
        }
        if (brand.isEmpty()) {
            list = repo.findByCatalogue(text, pageable);
            if (list.isEmpty() && needConvert) {
                list = repo.findByCatalogue(convert(text), pageable);
            }
            if (!list.isEmpty()) {
                catalogueId = list.get(0).getCatalogueId();
            }
        }
        text = text.trim();
        if (text.isEmpty() && !brand.isEmpty())
            return Collections.singletonList(new CatalogueElastic(list.get(0).getCatalogue(), list.get(0).getCatalogueId(), null, brand));
        text += "?";
        if (brand.isEmpty()) {
            type += "?";
            if (catalogueId == null) {
                list = repo.findAllByType(text, type, pageable);
                if (list.isEmpty()) {
                    list = repo.findAllByType(convert(text), type, pageable);
                }
            } else {
                list = repo.find(text, catalogueId, pageable);
                if (list.isEmpty()) {
                    list = repo.find(convert(text), catalogueId, pageable);
                }
            }

        } else {
            if (type.isEmpty()) {
                list = repo.findAllByBrand(text, brand, pageable);
                if (list.isEmpty()) {
                    list = repo.findAllByBrand(convert(text), brand, pageable);
                }
            } else {
                type += "?";
                list = repo.findAllByTypeAndBrand(text, brand, type, pageable);
                if (list.isEmpty()) {
                    list = repo.findAllByTypeAndBrand(convert(text), brand, type, pageable);
                }
            }
        }

        if (list.isEmpty()) {
            if (text2.contains(" "))
                text = String.join(" ", text.split("\\s"));
            text2 += "?";
            list = repo.findAllNotStrong(text2, pageable);
            if (list.isEmpty() && needConvert) {
                list = repo.findAllByTypeAndBrand(convert(text2), brand, type, pageable);
            }
        }
        return get(list, text, brand);
    }

    private List<CatalogueElastic> get(List<ItemElastic> list, String name, String brand) {
        Map<String, List<ItemElastic>> map = new HashMap<>();
        AtomicReference<ItemElastic> searchedItem = new AtomicReference<>();

        for (ItemElastic item : list) {
            String cleanedName = name.replace("?", "");
            if (cleanedName.equals(item.getName()) || (cleanedName.endsWith(item.getName()) && cleanedName.startsWith(item.getType()))) {
                searchedItem.set(item);
            }

            map.computeIfAbsent(item.getCatalogue(), k -> new ArrayList<>()).add(item);
        }

        if (brand.isEmpty()) {
            brand = null;
        }

        if (searchedItem.get() != null) {
            ItemElastic item = searchedItem.get();
            return Collections.singletonList(new CatalogueElastic(item.getCatalogue(), item.getCatalogueId(), Collections.singletonList(item), brand));
        }

        String finalBrand = brand;
        return map.keySet().stream()
                .map(c -> new CatalogueElastic(c, map.get(c).get(0).getCatalogueId(), map.get(c), finalBrand))
                .collect(Collectors.toList());
    }

    public List<CatalogueElastic> getByName(String num) {
        List<ItemElastic> list = new ArrayList<>();
        list = repo.findAllByName(".*" + num + ".*", pageable);
        return get(list, num, "");
    }

    public List<CatalogueElastic> getByItemId(String itemId) {
        var list = repo.findByItemId(itemId, PageRequest.of(0, 1));
        return Collections.singletonList(new CatalogueElastic(list.get(0).getCatalogue(), list.get(0).getCatalogueId(), list, list.get(0).getBrand()));
    }

    public static String convert(String message) {
        Map<Character, Character> mapToUse = message.matches(".*\\p{InCyrillic}.*") ? cyrillicToLatinMap : latinToCyrillicMap;
        StringBuilder builder = new StringBuilder();

        char[] chars = message.toCharArray();
        for (char c : chars) {
            builder.append(mapToUse.getOrDefault(c, c));
        }

        return builder.toString();
    }

    private Boolean isContainErrorChar(String text) {
        return text.contains("[") || text.contains("]") || text.contains("\"") || text.contains("/") || text.contains(";");
    }

}
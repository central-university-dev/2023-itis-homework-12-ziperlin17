package ru.shop.backend.search.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.shop.backend.search.model.*;
import ru.shop.backend.search.service.SearchService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;

@Slf4j
public class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController searchController;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSearchByItemAndCategory() {
        String searchText = "kak_delat_integr_testi...";
        int regionId = 1;
        SearchResult expectedResult = createSearchResult();
        when(searchService.getSearchResult(regionId, searchText)).thenReturn(expectedResult);

        ResponseEntity<SearchResult> responseEntity = searchController.searchByItemAndCategory(searchText, regionId);

        Assertions.assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        Assertions.assertEquals(expectedResult, responseEntity.getBody());
    }

    private SearchResult createSearchResult() {
        List<Item> items = new ArrayList<>();
        Item item = new Item();
        item.setPrice(10);
        item.setName("Laptop Pro 2022");
        item.setUrl("https://example.com/item102");
        item.setImage("i102");
        item.setItemId(102);
        item.setCat("laptop");
        items.add(item);

        List<Category> categories = new ArrayList<>();

        List<TypeHelpText> typeQueries = new ArrayList<>();
        typeQueries.add(new TypeHelpText(TypeOfQuery.SEE_ALSO, "laptop"));

        return new SearchResult(items, categories, typeQueries);
    }
}
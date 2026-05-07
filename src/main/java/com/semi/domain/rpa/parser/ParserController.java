package com.semi.domain.rpa.parser;

import com.semi.domain.keyword.TrendKeyword;
import com.semi.domain.keyword.TrendKeywordRepository;
import com.semi.domain.product.Product;
import com.semi.domain.rpa.parser.response.SupplierAndProductResponse;
import com.semi.domain.rpa.parser.response.RpaSupplierProductParseResult;
import com.semi.domain.rpa.parser.response.RpaTrendKeywordParseTarget;
import com.semi.domain.rpa.parser.response.TrendKeywordResponse;
import com.semi.domain.supplier.Supplier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@Validated // filterChain 설정시 filterChain에서 추가설정 필요, 동작안함
public class ParserController {
    // TrendKeyord
    private final TrendKeywordParserService trendKeywordParserService;
    private final TrendKeywordRepository trendKeywordRepository;
    private final SupplierAndProductService supplierAndProductService;
    private final RpaSupplierProductParsingService rpaSupplierProductParsingService;
    
    @GetMapping("/api/TrendKeywords") // http://localhost:8080/api/TrendKeywords
    public List<TrendKeywordResponse.TrendKeywordItem> keywordsFetch() {
            List<TrendKeywordResponse.TrendKeywordItem> result = trendKeywordParserService.getNaverKeywords();
        return result;
    }

    @GetMapping("/api/TrendKeywords/saveWithSequentialId") // http://localhost:8080/api/TrendKeywords/saveWithSequentialId
    public List<TrendKeyword> saveWithSequentialId() {
            List<TrendKeywordResponse.TrendKeywordItem> trendList = trendKeywordParserService.getNaverKeywords();
            List<TrendKeyword> result = trendKeywordParserService.saveWithSequentialId(trendList);

        return result;
    }

    @GetMapping("/api/TrendKeywords/rpaTargets") // http://localhost:8080/api/TrendKeywords/rpaTargets?size=20
    public List<RpaTrendKeywordParseTarget> rpaTargets(
        @RequestParam(defaultValue = "20") int size
    ) {
        return trendKeywordParserService.getTodayRpaParseTargets(size);
    }

    @GetMapping("/api/Products/rpaSaveToday") // http://localhost:8080/api/Products/rpaSaveToday?size=20
    public List<RpaSupplierProductParseResult> productsRpaSaveToday(
        @RequestParam(defaultValue = "20") int size
    ) {
        return rpaSupplierProductParsingService.parseTodaySupplierAndProducts(size);
    }


    @GetMapping("/api/Suppliers") // http://localhost:8080/api/Suppliers?rankId=2182837573&syncDate=20260429
    public SupplierAndProductResponse suppliersFetch(
        @RequestParam @NotNull (message = "rankId는 필수 입력값입니다.") Long rankId,
        @RequestParam @NotBlank(message = "syncDate는 필수 입력값입니다.") String syncDate
    ) {
        SupplierAndProductResponse result = supplierAndProductService.getNaverSuppliers(rankId, syncDate);

        return result;
    }    

    //[x]TODO 테스트가 끝나면 막을 것, 제품 공급자 api는 호출 할 때 마다 다른값이 나옴으로 동시에 저장 필요.
    // @GetMapping("/api/Suppliers/saveWithSequentialId") // http://localhost:8080/api/Suppliers/saveWithSequentialId?rankId=2182837573&syncDate=20260429
    public List<Supplier> suppliersSaveWithSequentialId(
        @RequestParam @NotNull (message = "rankId는 필수 입력값입니다.") Long rankId,
        @RequestParam @NotBlank(message = "syncDate는 필수 입력값입니다.") String syncDate
    ) {
        SupplierAndProductResponse supplierAndProductResponse = supplierAndProductService.getSupplierAndProducts(rankId, syncDate);
        List<Supplier> result = supplierAndProductService.saveSuppliersWithSequentialId(supplierAndProductResponse);

        return result;
    }


    // Product
    // Reason: Product parser test data contains products under the supplier/product response root.
    // Behavior: Fetch the Naver detail response and return only the product list for parser verification.
    // Source: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
    @GetMapping("/api/Products") // http://localhost:8080/api/Products?rankId=2182837573&syncDate=20260429
    public List<SupplierAndProductResponse.ProductItem> productsFetch(
        @RequestParam @NotNull(message = "rankId는 필수 입력값입니다.") Long rankId,
        @RequestParam @NotBlank(message = "syncDate는 필수 입력값입니다.") String syncDate
    ) {
        SupplierAndProductResponse supplierAndProductResponse = supplierAndProductService.getSupplierAndProducts(rankId, syncDate);
        List<SupplierAndProductResponse.ProductItem> result = supplierAndProductResponse.getProductList();

        if (result == null) {
            return List.of();
        }

        return result;
    }

    // Reason: Product needs an existing TrendKeyword foreign key before it can be stored.
    // Behavior: Load TrendKeyword by keywordId, fetch parser products, save related suppliers first, then save products.
    // Source: https://docs.spring.io/spring-data/jpa/reference/repositories/core-concepts.html
    // http://localhost:8080/api/Products/saveWithSequentialId?keywordId=160&rankId=2179193963&syncDate=20260428
    @GetMapping("/api/Products/saveWithSequentialId") // http://localhost:8080/api/Products/saveWithSequentialId?keywordId=160&rankId=2179193963&syncDate=20260428
    public List<Product> productsSaveWithSequentialId(
        @RequestParam @NotNull(message = "keywordId는 필수 입력값입니다.") Long keywordId,
        @RequestParam @NotNull(message = "rankId는 필수 입력값입니다.") Long rankId,
        @RequestParam @NotBlank(message = "syncDate는 필수 입력값입니다.") String syncDate
    ) {
        TrendKeyword trendKeyword = trendKeywordRepository.findById(keywordId)
            .orElseThrow(() -> new IllegalArgumentException("keywordId에 해당하는 TrendKeyword가 없습니다. keywordId=" + keywordId));
        SupplierAndProductResponse supplierAndProductResponse = supplierAndProductService.getSupplierAndProducts(rankId, syncDate);
        List<Product> result = supplierAndProductService.saveSupplierAndProductsWithSequentialId(trendKeyword, supplierAndProductResponse);

        return result;
    }



}

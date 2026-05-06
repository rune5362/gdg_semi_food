package com.semi.domain.rpa.parser;

import com.semi.domain.keyword.TrendKeyword;
import com.semi.domain.keyword.TrendKeywordRepository;
import com.semi.domain.product.Product;
import com.semi.domain.rpa.parser.response.SupplierAndProductResponse;
import com.semi.domain.rpa.parser.response.TrendKeywordResponse;
import com.semi.domain.supplier.Supplier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/*
[ ]TODO 파싱 RPA 시퀀스 만들기 ( 작성중 )
파싱 RPA 시퀀스{
    1. http://localhost:8080/api/TrandKeywords/saveWithSequentialId 에 접속하여 당일 트랜드 데이터를 저장 함. size = 20
    2. http://localhost:8080/api/Products/saveWithSequentialId?keywordId=160&rankId=2179193963&syncDate=20260428 에 접속하여 서플라이어와 제품을 저장함.
        - 1.에서 얻은 keywordId는 size = 20이므로, TrandKeywords 테이블에서 가장 마지막 20개의 항목을 기준으로 keywordId, rankId, syncDate를 조합하여 20회 반복하면서 저장.
        - 제품과 공급자 데이터는 매번 달라지므로 주의.
            - RPA에서 금일 실행중인 날짜와 트랜드 순위를 같이 기록해둘 필요가 있음.

    3. 구현방식

    4. 로그 저장방식
        테이블에는 message를 통해서 어떤 데이터가 저장되었는지 기록, 로그파일에는 RPA 실행 시점과 어떤 데이터가 저장되었는지/어떤 디버깅 로그가 출력됐는지 상세히 기록.
    

RPA 테이블 구조 : \src\main\resources\db\migration\V6__create_rpa_log_and_audit_tables.sql
RPA 로그 저장 장소 : \src\main\resources\static\test\rpa\log
log파일의 형식은 rpa_parsing_yymmdd_time.log

}

[ ]TODO 대시보드 만들기 (작성중 )
    1. admin 로그인 시에만 뜨는 rpa/dashboard.html 화면 구현:{
        - 동적페이지를 기본으로 CRUD api로 정보를 조회함.
        - 한 화면에 총4개의 뷰를 가지고 각각 trend_keyword, supplier, product, rpa_log 를 설정한 기간 날짜만큼 보여주고 각 뷰안에서 스크롤이 가능함.
        - 각 뷰의 상단에는 기본 CRUD를 진행 할 수 있는 버튼이 있으며, rpa_log.status 의 값에 따라 잠기도록 구현 필요
        - SSE (Server-Sent Events)를 이용하여 RPA가 실행중일 때 실시간으로 로그가 업데이트 되도록 구현 필요
        - SSE (Server-Sent Events)를 이용하여 DB에 반영된 데이터를 실시간으로 업데이트 하도록 구현 필요
        
    }
    2. 각각 trend_keyword, supplier, product의 CRUD 구현
            Ex) - RPA의 대상이 되는 각각 
                    package com.semi.domain.keyword;
                    package com.semi.domain.supplier;
                    package com.semi.domain.product;
                    의 Repository에 start = LocalDate.now().atStartOfDay(); findAllByCreatedAtGreaterThanEqual(LocalDateTime start); 와 비슷한 형식의 각 CRUD 구현 필요
                - RPA 실행 시점의 날짜를 기준으로 데이터를 삭제하는 기능 구현 필요 (ex. deleteByCreatedAtGreaterThanEqual(LocalDateTime start);)
        2-1. 종속성에 대비해 당일을 기준으로 product, suplier, trend_keyword순으로 삭제하도록 구현
        2-2. 종속성에 대비해 당일을 기준으로 trend_keyword, supplier, product순으로 파싱 하는 기능 필요 
            2-2-1. 파싱을 할 때 당일 파싱한 trend_keyword list를 기준으로 반복문을 돌면서 파싱하는 기능 필요
            2-2-2. 파싱이 중 단 될 때를 대비하여 trend_keyword의 진행상황을 기록하는 기능과 오늘치 trend_keyword 을 삭제 후 다시 작업하는 기능 필요.
    
*/

@RestController
@RequiredArgsConstructor
@Validated // filterChain 설정시 filterChain에서 추가설정 필요, 동작안함
public class ParserController {
    // TrendKeyord
    private final TrendKeywordService trendKeywordService;
    private final TrendKeywordRepository trendKeywordRepository;
    private final SupplierAndProductService supplierAndProductService;
    
    @GetMapping("/api/TrandKeywords") // http://localhost:8080/api/TrandKeywords
    public List<TrendKeywordResponse.TrendKeywordItem> keywordsFetch() {
            List<TrendKeywordResponse.TrendKeywordItem> result = trendKeywordService.getNaverKeywords();
        return result;
    }

    @GetMapping("/api/TrandKeywords/saveWithSequentialId") // http://localhost:8080/api/TrandKeywords/saveWithSequentialId
    public List<TrendKeyword> saveWithSequentialId() {
            List<TrendKeywordResponse.TrendKeywordItem> trendList = trendKeywordService.getNaverKeywords();
            List<TrendKeyword> result = trendKeywordService.saveWithSequentialId(trendList);

        return result;
    }


    @GetMapping("/api/Suppliers") // http://localhost:8080/api/Suppliers?rankId=2182837573&syncDate=20260429
    public SupplierAndProductResponse suppliersFetch(
        @RequestParam @NotNull (message = "rankId는 필수 입력값입니다.") Long rankId,
        @RequestParam @NotBlank(message = "syncDate는 필수 입력값입니다.") String syncDate
    ) {
        SupplierAndProductResponse result = supplierAndProductService.getNaverSuppliers(rankId, syncDate);

        return result;
    }    

    //[ ]TODO 테스트가 끝나면 막을 것, 제품 공급자 api는 호출 할 때 마다 다른값이 나옴으로 동시에 저장 필요.
    @GetMapping("/api/Suppliers/saveWithSequentialId") // http://localhost:8080/api/Suppliers/saveWithSequentialId?rankId=2182837573&syncDate=20260429
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

package com.semi.domain.rpa.parser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.semi.domain.keyword.TrendKeyword;
import com.semi.domain.product.Product;
import com.semi.domain.product.ProductCategoryClassifier;
import com.semi.domain.product.ProductRepository;
import com.semi.domain.rpa.parser.mapper.ProductMapper;
import com.semi.domain.rpa.parser.mapper.SupplierMapper;
import com.semi.domain.rpa.parser.response.SupplierAndProductResponse;
import com.semi.domain.rpa.parser.response.SupplierAndProductResponse.ProductItem;
import com.semi.domain.rpa.parser.response.SupplierAndProductResponse.SupplierItem;
import com.semi.domain.supplier.Supplier;
import com.semi.domain.supplier.SupplierRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierAndProductService {

    private final RestClient restClient = RestClient.create();
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SupplierMapper supplierMapper;
    private final ProductMapper productMapper;

    public SupplierAndProductResponse getNaverSuppliers(Long sapRankId, String sapSyncDate) {
        String sapTargetSiteUrl = String.format(Constants.Snxbest.TARGET_SITE_URL_TEMPLATE, sapRankId, sapSyncDate);
        String sapTargetDataUrl = String.format(Constants.Snxbest.API_V1_FOOD_PRODUCT_DATA_URL_TEMPLATE , sapRankId, sapSyncDate);

        final String sapRawData = sapRetry("네이버 공급자/상품 원문 조회", () -> restClient.get()
            .uri(sapTargetDataUrl)
            .header("User-Agent", Constants.Http.USER_AGENT)
            .header("Accept", MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE)
            .header("Referer", sapTargetSiteUrl)
            .retrieve()
            .body(String.class));
        RpaLogContext.info(log, "네이버 공급자/상품 응답 원문: " + sapRawData);

        SupplierAndProductResponse response = sapRetry("네이버 공급자/상품 상세 조회", () -> restClient.get()
            .uri(sapTargetDataUrl)
            .header("User-Agent", Constants.Http.USER_AGENT)
            .header("Accept",  MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE)
            .header("Accept-Language", Constants.Http.ACCEPT_LANGUAGE)
            .header("Referer", sapTargetSiteUrl)
            .retrieve()
            .body(SupplierAndProductResponse.class)); 
        RpaLogContext.info(log, "수신된 공급자/상품 데이터: " + response);
        return response;
    }

    public SupplierAndProductResponse getSupplierAndProducts(Long sapRankId, String sapSyncDate) {
        String sapTargetSiteUrl = String.format(Constants.Snxbest.TARGET_SITE_URL_TEMPLATE, sapRankId, sapSyncDate);
        String sapTargetDataUrl = String.format(Constants.Snxbest.API_V1_FOOD_PRODUCT_DATA_URL_TEMPLATE , sapRankId, sapSyncDate);

        SupplierAndProductResponse sapResponse = sapRetry("네이버 공급자/상품 조회", () -> restClient.get()
            .uri(sapTargetDataUrl)
            .header("User-Agent", Constants.Http.USER_AGENT)
            .header("Accept", MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE)
            .header("Accept-Language", Constants.Http.ACCEPT_LANGUAGE)
            .header("Referer", sapTargetSiteUrl)
            .retrieve()
            .body(SupplierAndProductResponse.class));

        RpaLogContext.info(log, "수신된 공급자/상품 데이터: " + sapResponse);
        return sapResponse;
    }

    @Transactional(readOnly = true)
    public boolean hasTodayProductsForKeywordAndSyncDate(TrendKeyword sapKeyword, String sapSyncDate) {
        LocalDate sapToday = LocalDate.now();
        LocalDateTime sapCrawledStart = sapToday.atStartOfDay();
        LocalDateTime sapCrawledEnd = sapToday.plusDays(1).atStartOfDay();
        LocalDateTime sapParsedSyncDate = LocalDate.parse(sapSyncDate, DateTimeFormatter.BASIC_ISO_DATE)
            .atStartOfDay();

        return productRepository.existsRpaCategoryProcessedToday(
            sapKeyword.getKeyword(),
            sapKeyword.getRank(),
            sapParsedSyncDate,
            sapCrawledStart,
            sapCrawledEnd
        );
    }

    @Transactional
    public List<Supplier> saveSuppliersWithSequentialId(SupplierAndProductResponse sapResponse) {
        List<ProductItem> sapProductItems = sapGetProductItems(sapResponse);
        

        ArrayList<Supplier> result = new ArrayList<>(sapBuildSupplierMap(sapProductItems).values());


        return result;
    }


    private Map<String, Supplier> sapBuildSupplierMap(List<ProductItem> sapProductItems) {
        Map<String, Supplier> sapSupplierMap = new LinkedHashMap<>();
        List<SupplierItem> sapSupplierItems = sapExtractSupplierItems(sapProductItems);

        if (sapSupplierItems.isEmpty()) {
            return sapSupplierMap;
        }

        Long sapMaxId = supplierRepository.findMaxId();
        long sapNextId = (sapMaxId == null) ? 0L : sapMaxId;


        // 2. DTO -> VO 변환 (MapStruct 사용)
        final List<Supplier> parsedSuppliers = supplierMapper.toVoList(sapSupplierItems); // 파싱된 데이터
        if (parsedSuppliers == null || parsedSuppliers.isEmpty()) {
            RpaLogContext.info(log, "파싱된 공급자 데이터가 없습니다.");
            return Map.of();
        }

        List<Supplier> sapNewSuppliers = new ArrayList<>();

        for (SupplierItem sapSupplierItem : sapSupplierItems) {
            Supplier sapMappedSupplier = supplierMapper.toVo(sapSupplierItem);
            String sapSupplierName = sapNormalizeText(sapMappedSupplier.getName(), Constants.Db.UNKNOWN);
            String sapSupplierUrl = sapNormalizeText(sapMappedSupplier.getUrl(), Constants.Db.UNKNOWN);


            Supplier sapExistingSupplier = supplierRepository.findByName(sapSupplierName).orElse(null);
            if (sapExistingSupplier != null) {
                sapSupplierMap.put(sapSupplierName, sapExistingSupplier);
                continue;
            }

            if (supplierRepository.existsByCreatedAtAndName(sapMappedSupplier.getCreatedAt(), sapSupplierName)) {
                Supplier sapDuplicatedSupplier = supplierRepository.findByName(sapSupplierName).orElse(null);
                if (sapDuplicatedSupplier != null) {
                    sapSupplierMap.put(sapSupplierName, sapDuplicatedSupplier);
                }
                continue;
            }

            Supplier sapNewSupplier = Supplier.builder()
                    .id(++sapNextId)
                    .name(sapSupplierName)
                    .url(sapSupplierUrl)
                    .createdAt(sapMappedSupplier.getCreatedAt())
                    .build();
                    
            sapNewSuppliers.add(sapNewSupplier);
            sapSupplierMap.put(sapSupplierName, sapNewSupplier);
        }

        if (!sapNewSuppliers.isEmpty()) {
            supplierRepository.saveAllAndFlush(sapNewSuppliers);
            RpaLogContext.info(log, "{}건의 Supplier 저장 완료 (마지막 ID: {})", sapNewSuppliers.size(), sapNextId);
        }

        return sapSupplierMap;
    }

    private List<SupplierItem> sapExtractSupplierItems(List<ProductItem> sapProductItems) {
        Map<String, SupplierItem> sapSupplierItemMap = new LinkedHashMap<>();

        for (ProductItem sapProductItem : sapProductItems) {
            SupplierItem sapSupplierItem = new SupplierItem();
            String sapSupplierName = sapNormalizeText(sapProductItem.getMallNm(), Constants.Db.UNKNOWN);
            String sapSupplierUrl = sapNormalizeText(sapProductItem.getMallLinkUrl(), Constants.Db.UNKNOWN);

            if( ! (sapSupplierUrl.equals(Constants.Db.UNKNOWN))  ){
                sapSupplierName = sapSupplierUrl.split("/")[sapSupplierUrl.split("/").length - 1] + "_" + sapSupplierName;
            }

            sapSupplierItem.setMallNm(sapSupplierName);
            sapSupplierItem.setMallLinkUrl(sapSupplierUrl);
            sapSupplierItem.setSyncDate(sapProductItem.getSyncDate());

            sapSupplierItemMap.putIfAbsent(sapSupplierName, sapSupplierItem);
        }

        return new ArrayList<>(sapSupplierItemMap.values());
    }





    @Transactional
    public List<Product> saveSupplierAndProductsWithSequentialId(TrendKeyword sapKeyword, SupplierAndProductResponse sapResponse) {
        if (sapKeyword == null) {
            throw new IllegalArgumentException("TrendKeyword 정보 없이 Product를 저장할 수 없습니다.");
        }

        List<ProductItem> sapProductItems = sapGetProductItems(sapResponse);
        if (sapProductItems.isEmpty()) {
            return new ArrayList<>();
        }

        // List<Supplier> sapSupplierList = saveSuppliersWithSequentialId(sapResponse); // 공급자 먼저 저장하여 제품과의 연관 관계를 보장
        Map<String, Supplier> sapSupplierMap = sapBuildSupplierMap(sapProductItems);

        Long sapMaxId = productRepository.findMaxId();
        long sapNextId = (sapMaxId == null) ? 0L : sapMaxId;

        
        // 2. DTO -> VO 변환 (MapStruct 사용)
        final List<Product> parsedProducts = productMapper.toVoList(sapProductItems); // 파싱된 데이터
        if (parsedProducts == null || parsedProducts.isEmpty()) {
            RpaLogContext.info(log, "파싱된 제품 데이터가 없습니다.");
            return List.of();
        }

        final Product latestParsedProduct = parsedProducts.get(0);
        final Product latestSavedRecord = productRepository.findFirstByOrderByIdDesc();

        // 파싱한 데이터가 기존 데이터보다 최신이 아니라면 안내용 리스트 반환
        if (latestSavedRecord != null 
            && latestParsedProduct.getCrawledAt().toLocalDate().isBefore(latestSavedRecord.getCrawledAt().toLocalDate())) {
            RpaLogContext.info(log, "동일하거나 이전 날짜의 제품 데이터입니다. 저장하지 않습니다. "
                + "파싱 날짜: " + latestParsedProduct.getCrawledAt().toLocalDate()
                + ", DB 최신 날짜: " + latestSavedRecord.getCrawledAt().toLocalDate());            
            return List.of();
            
        }

        List<Product> sapMappedProducts = productMapper.toVoList(sapProductItems);
        List<Product> sapNewProducts = new ArrayList<>();

        
        for (int sapIndex = 0; sapIndex < sapMappedProducts.size(); sapIndex++) {
            Product sapMappedProduct = sapMappedProducts.get(sapIndex);
            ProductItem sapSourceItem = sapProductItems.get(sapIndex);

            if (sapProductAlreadySavedToday(sapKeyword, sapMappedProduct)) {
                continue;
            }

            String sapSupplierName = sapNormalizeText(sapSourceItem.getMallNm(), Constants.Db.UNKNOWN);
            String sapSupplierUrl = sapNormalizeText(sapSourceItem.getMallLinkUrl(), Constants.Db.UNKNOWN);
            if( ! (sapSupplierUrl.equals(Constants.Db.UNKNOWN))  ){
                sapSupplierName = sapSupplierUrl.split("/")[sapSupplierUrl.split("/").length - 1] + "_" + sapSupplierName;
            }

            // Supplier sapSupplier = sapSupplierList.get(sapSupplierName);
            Supplier sapSupplier = sapSupplierMap.get(sapSupplierName);

            Product sapProduct = Product.builder()
                .id(++sapNextId)
                .keyword(sapKeyword)
                .supplier(sapSupplier)
                .name(sapMappedProduct.getName())
                .description(null)
                .price(sapMappedProduct.getPrice())
                .imageUrl(sapMappedProduct.getImageUrl())
                .productUrl(sapMappedProduct.getProductUrl())
                .autoOrder(false)
                .crawledAt(sapMappedProduct.getCrawledAt())
                .syncDate(sapMappedProduct.getSyncDate())
                .stock(0) // 머지하고 하루뒤부터 재고 정보가 없으면 에러가 떠서 추가 2026_05_08_Fri_1201
                .availableStock(0) // 머지하고 하루뒤부터 재고 정보가 없으면 에러가 떠서 추가 2026_05_08_Fri_1204
                .category(ProductCategoryClassifier.classify(sapMappedProduct.getName()))
                .build();
            sapNewProducts.add(sapProduct);
        }

        if (!sapNewProducts.isEmpty()) {
            productRepository.saveAllAndFlush(sapNewProducts);
            RpaLogContext.info(log, "{}건의 Product 저장 완료 (마지막 ID: {})", sapNewProducts.size(), sapNextId);
        }

        return sapNewProducts;
    }

    private boolean sapProductAlreadySavedToday(TrendKeyword sapKeyword, Product sapMappedProduct) {
        if (sapMappedProduct.getCrawledAt() == null || sapMappedProduct.getSyncDate() == null) {
            return false;
        }

        LocalDate sapCrawledDate = sapMappedProduct.getCrawledAt().toLocalDate();
        LocalDateTime sapCrawledStart = sapCrawledDate.atStartOfDay();
        LocalDateTime sapCrawledEnd = sapCrawledDate.plusDays(1).atStartOfDay();

        return productRepository.existsRpaProductToday(
            sapKeyword.getKeyword(),
            sapKeyword.getRank(),
            sapMappedProduct.getName(),
            sapMappedProduct.getSyncDate(),
            sapCrawledStart,
            sapCrawledEnd
        );
    }


    private List<ProductItem> sapGetProductItems(SupplierAndProductResponse sapResponse) {
        if (sapResponse == null || sapResponse.getProductList() == null) {
            return new ArrayList<>();
        }
        return sapResponse.getProductList();
    }

    private String sapNormalizeText(String sapValue, String sapDefaultValue) {
        if (sapValue == null || sapValue.isBlank()) {
            return sapDefaultValue;
        }
        return sapValue.trim();
    }

    private <T> T sapRetry(String sapActionName, java.util.function.Supplier<T> sapAction) {
        RuntimeException sapLastException = null;
        for (int sapAttempt = 1; sapAttempt <= 3; sapAttempt++) {
            try {
                return sapAction.get();
            } catch (RuntimeException sapException) {
                sapLastException = sapException;
                RpaLogContext.warn(log, "{} 실패. attempt={}/3", sapActionName, sapAttempt, sapException);
            }
        }
        throw sapLastException;
    }
}

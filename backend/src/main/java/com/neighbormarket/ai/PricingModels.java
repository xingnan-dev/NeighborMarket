package com.neighbormarket.ai;
import com.neighbormarket.domain.TradeType;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.util.List;
public final class PricingModels {private PricingModels(){}
 public enum Status{NEED_MORE_INFO,ESTIMATE} public enum Confidence{LOW,MEDIUM,HIGH}
 public record Request(@NotNull(message="请选择交易方式")TradeType tradeType,@Size(max=100)String title,@Size(max=50)String category,@Size(max=2000)String description,@Size(max=120)String brandModel,@DecimalMin(value="0.0",inclusive=true)@Digits(integer=8,fraction=2)BigDecimal purchasePrice,@Size(max=100)String usageDuration,@Size(max=300)String condition,@Size(max=500)String defects,@Size(max=300)String accessories){}
 public record Result(Status status,List<String> missingFields,List<String> questions,BigDecimal minPrice,BigDecimal maxPrice,BigDecimal recommendedPrice,String rationale,List<String> factors,String uncertainty,Confidence confidence){}
}
package com.neighbormarket.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neighbormarket.domain.TradeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PricingService {
 static final BigDecimal MAX_PRICE=new BigDecimal("1000000");
 private final PricingGateway gateway;
 private final ObjectMapper json;
 private final Clock clock;
 private final Duration cooldown;
 private final AiPricingTelemetry telemetry;
 private final ConcurrentHashMap<String,Instant> lastCalls=new ConcurrentHashMap<>();

 @Autowired
 public PricingService(PricingGateway gateway,ObjectMapper json,AiPricingTelemetry telemetry){this(gateway,json,Clock.systemDefaultZone(),Duration.ofSeconds(60),telemetry);}
 PricingService(PricingGateway gateway,ObjectMapper json,Clock clock,Duration cooldown){this(gateway,json,clock,cooldown,AiPricingTelemetry.noop());}
 PricingService(PricingGateway gateway,ObjectMapper json,Clock clock,Duration cooldown,AiPricingTelemetry telemetry){this.gateway=gateway;this.json=json;this.clock=clock;this.cooldown=cooldown;this.telemetry=telemetry;}

 public PricingModels.Result estimate(PricingModels.Request request,String username){
  long started=System.nanoTime();
  try{
   if(request.tradeType()!=TradeType.FIXED_PRICE)throw new PricingException(HttpStatus.BAD_REQUEST,"AI_FIXED_PRICE_ONLY","AI估价仅适用于标价物品");
   reserve(username);
   PricingModels.Result result=parseAndValidate(gateway.estimate(request));
   telemetry.record(username,result.status()==PricingModels.Status.ESTIMATE?"estimate":"need_more_info",System.nanoTime()-started);
   return result;
  }catch(PricingException exception){
   telemetry.record(username,outcomeFor(exception.code()),System.nanoTime()-started);
   throw exception;
  }catch(RuntimeException exception){
   telemetry.record(username,"unexpected_error",System.nanoTime()-started);
   throw exception;
  }
 }
 private String outcomeFor(String code){return switch(code){
  case "AI_TOO_FREQUENT"->"local_rate_limited";
  case "AI_RATE_LIMITED"->"provider_rate_limited";
  case "AI_TIMEOUT"->"timeout";
  case "AI_NOT_CONFIGURED"->"not_configured";
  case "AI_PROVIDER_UNSUPPORTED"->"provider_unsupported";
  case "AI_PROVIDER_ERROR"->"provider_error";
  case "AI_REQUEST_FAILED"->"request_failed";
  case "AI_INVALID_RESPONSE"->"invalid_response";
  case "AI_INTERRUPTED"->"interrupted";
  case "AI_FIXED_PRICE_ONLY"->"rejected_trade_type";
  default->"other_error";
 };}
 private void reserve(String user){Instant now=clock.instant();lastCalls.compute(user,(k,last)->{if(last!=null&&now.isBefore(last.plus(cooldown)))throw new PricingException(HttpStatus.TOO_MANY_REQUESTS,"AI_TOO_FREQUENT","请求过于频繁，请稍后再试");return now;});}
 PricingModels.Result parseAndValidate(String raw){try{JsonNode n=json.readTree(raw);if(n==null||!n.isObject())throw invalid();PricingModels.Status status=PricingModels.Status.valueOf(requiredText(n,"status",30));if(status==PricingModels.Status.NEED_MORE_INFO){List<String> fields=textArray(n,"missingFields",8,80,true),questions=textArray(n,"questions",8,200,true);return new PricingModels.Result(status,fields,questions,null,null,null,null,List.of(),null,null);}BigDecimal min=money(n,"minPrice"),max=money(n,"maxPrice"),recommended=money(n,"recommendedPrice");if(min.compareTo(recommended)>0||recommended.compareTo(max)>0)throw invalid();String rationale=requiredText(n,"rationale",1000),uncertainty=requiredText(n,"uncertainty",600);List<String> factors=textArray(n,"factors",10,200,true);PricingModels.Confidence confidence=PricingModels.Confidence.valueOf(requiredText(n,"confidence",20));return new PricingModels.Result(status,List.of(),List.of(),min,max,recommended,rationale,factors,uncertainty,confidence);}catch(PricingException e){throw e;}catch(Exception e){throw invalid();}}
 private BigDecimal money(JsonNode n,String field){JsonNode v=n.get(field);if(v==null||!v.isNumber())throw invalid();BigDecimal x=v.decimalValue();if(x.signum()<0||x.compareTo(MAX_PRICE)>0||x.scale()>2)throw invalid();return x;}
 private String requiredText(JsonNode n,String field,int max){JsonNode v=n.get(field);if(v==null||!v.isTextual())throw invalid();String x=v.asText().trim();if(x.isEmpty()||x.length()>max)throw invalid();return x;}
 private List<String> textArray(JsonNode n,String field,int maxItems,int maxLength,boolean nonEmpty){JsonNode a=n.get(field);if(a==null||!a.isArray()||a.size()>maxItems||(nonEmpty&&a.isEmpty()))throw invalid();List<String> out=new ArrayList<>();for(JsonNode v:a){if(!v.isTextual())throw invalid();String x=v.asText().trim();if(x.isEmpty()||x.length()>maxLength)throw invalid();out.add(x);}return List.copyOf(out);}
 private PricingException invalid(){return new PricingException(HttpStatus.BAD_GATEWAY,"AI_INVALID_RESPONSE","AI返回结果无效，可继续手动填写价格");}
}

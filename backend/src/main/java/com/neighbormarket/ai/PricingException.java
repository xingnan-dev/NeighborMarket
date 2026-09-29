package com.neighbormarket.ai;
import org.springframework.http.HttpStatus;
public class PricingException extends RuntimeException {private final HttpStatus status;private final String code;public PricingException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}public HttpStatus status(){return status;}public String code(){return code;}}
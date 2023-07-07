package com.amrok.product.command.kafka;

import com.amrok.product.command.dto.ProductOrderCreateDto;
import com.amrok.product.command.services.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.net.UnknownHostException;

@Component
public class ProductConsumer {
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProductService productService;

    @KafkaListener(topics = "${kafka.topic.product.order.create}", groupId = "${kafka.topic.group-id}")
    public void consumeProductOrderCreate(String message, Acknowledgment ack) throws UnknownHostException {
        try {
            System.out.println("Received [kafka.topic.product.order.create] message: " + message);
            ProductOrderCreateDto productOrderCreateDto = mapper.readValue(message, ProductOrderCreateDto.class);
            System.out.println("Parsing to DTO");
            productService.orderCreated(productOrderCreateDto);
        } catch (Exception e) {
            String msg = "xxxxx Error occurred xxxxx";
            System.out.println("Received [kafka.topic.product.category.create] message: " + message + e);
        }
        ack.acknowledge();
    }
}

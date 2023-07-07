package com.amrok.product.command.services;

import com.amrok.product.command.dto.ProductCategoryCreateDto;
import com.amrok.product.command.dto.ProductCategoryUpdateDto;
import com.amrok.product.command.kafka.ProductProducer;
import com.amrok.product.command.models.ProductCategory;
import com.amrok.product.command.repositories.ProductCategoryRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCategoryService {

    @Value("${kafka.topic.product.category.create}")
    private String TOPIC_PRODUCT_CATEGORY_CREATE;
    @Value("${kafka.topic.product.category.update}")
    private String TOPIC_PRODUCT_CATEGORY_UPDATE;
    @Value("${kafka.topic.product.category.delete}")
    private String TOPIC_PRODUCT_CATEGORY_DELETE;

    @Autowired
    private ProductProducer productProducer;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    public List<ProductCategory> findAll() {
        List<ProductCategory> productCategories = productCategoryRepository.findAll();
        return productCategories;
    }

    public ProductCategory findById(Long id) {
        return productCategoryRepository.findById(id).get();
    }

    public ProductCategory create(ProductCategoryCreateDto productCategoryCreateDto) {
        ProductCategory productCategory = new ProductCategory();
        productCategory.setName(productCategoryCreateDto.getName());
        productCategory.setImageUrl(productCategoryCreateDto.getImageUrl());
        productCategory.setDistrictId(productCategoryCreateDto.getDistrictId());
        productCategory.setOrderNo(productCategoryCreateDto.getOrderNo());
        ProductCategory newProductCategory = productCategoryRepository.save(productCategory);
        productProducer.produce(TOPIC_PRODUCT_CATEGORY_CREATE, newProductCategory);
        return newProductCategory;
    }

    public ProductCategory update(Long id, ProductCategoryUpdateDto productCategoryUpdateDto) {
        ProductCategory productCategory = findById(id);
        if (productCategoryUpdateDto.getName() != null) {
            productCategory.setName(productCategoryUpdateDto.getName());
        }

        if (productCategoryUpdateDto.getDistrictId() != null) {
            productCategory.setDistrictId(productCategoryUpdateDto.getDistrictId());
        }

        if (productCategoryUpdateDto.getImageUrl() != null) {
            productCategory.setImageUrl(productCategoryUpdateDto.getImageUrl());
        }

        if (productCategoryUpdateDto.getOrderNo() != null) {
            productCategory.setOrderNo(productCategoryUpdateDto.getOrderNo());
        }
        ProductCategory newProductCategory = productCategoryRepository.save(productCategory);
        productProducer.produce(TOPIC_PRODUCT_CATEGORY_UPDATE, newProductCategory);
        return newProductCategory;
    }

    public void delete(Long id) {
        ProductCategory productCategory = findById(id);
        productProducer.produce(TOPIC_PRODUCT_CATEGORY_DELETE, id);
        productCategoryRepository.delete(productCategory);
    }
}

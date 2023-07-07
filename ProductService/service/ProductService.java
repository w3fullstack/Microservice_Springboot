package com.amrok.product.command.services;

import com.amrok.product.command.dto.ProductCreateDto;
import com.amrok.product.command.dto.ProductOrderCreateDto;
import com.amrok.product.command.dto.ProductUpdateDto;
import com.amrok.product.command.kafka.ProductProducer;
import com.amrok.product.command.models.Product;
import com.amrok.product.command.models.ProductKind;
import com.amrok.product.command.models.ProductSpec;
import com.amrok.product.command.repositories.ProductCategoryRepository;
import com.amrok.product.command.repositories.ProductKindRepository;
import com.amrok.product.command.repositories.ProductRepository;
import com.amrok.product.command.repositories.ProductSpecRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.util.stream.Collectors;

@Service
public class ProductService {
    @Value("${kafka.topic.product.create}")
    private String TOPIC_PRODUCT_CREATE;
    @Value("${kafka.topic.product.update}")
    private String TOPIC_PRODUCT_UPDATE;
    @Value("${kafka.topic.product.delete}")
    private String TOPIC_PRODUCT_DELETE;

    @Autowired
    private ProductProducer productProducer;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpecRepository productSpecRepository;

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Autowired
    private ProductKindRepository productKindRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Find and Filter All products
     *
     * @param pageable
     * @return
     */
    public Page<Product> findAll(Pageable pageable) {
        Page<Product> products = productRepository.findAll(pageable);
        products.getContent().forEach(product -> {
            product.getKind().getCategory().setKinds(null);
        });
        return products;
    }

    /**
     * Find Product By id
     *
     * @param id
     * @return
     */
    public Product findById(Long id) {
        return productRepository.findById(id).get();
    }

    /**
     * Create Product
     *
     * @param productCreateDto
     * @param adminId
     * @return
     */
    public Product create(ProductCreateDto productCreateDto, Long adminId) {
        ProductKind productKind = productKindRepository.findById(productCreateDto.getKindId()).get();
        ObjectMapper objectMapper = new ObjectMapper();

        Product product = new Product();
        product.setName(productCreateDto.getName());
        product.setCreatedBy(adminId);
        product.setUpdatedBy(adminId);
        product.setCoverMedia(productCreateDto.getCoverMedia());
        product.setDetailMedia(productCreateDto.getDetailMedia());
        product.setMeta(productCreateDto.getMeta());
        product.setStatus(productCreateDto.getStatus());
        product.setDescription(productCreateDto.getDescription());
        product.setOrderNo(productCreateDto.getOrderNo());
        product.setKind(productKind);
        product.setSoldCount(0);
        product.setSpecs(productCreateDto.getSpecs().stream().map(productSpecCreateDto -> {
            ProductSpec productSpec = new ProductSpec();
            productSpec.setName(productSpecCreateDto.getName());
            productSpec.setPrice(productSpecCreateDto.getPrice());
            productSpec.setCoin(productSpecCreateDto.getCoin());
            productSpec.setOrgPrice(productSpecCreateDto.getOrgPrice());
            productSpec.setCount(productSpecCreateDto.getCount());
            productSpec.setOrderNo(productSpecCreateDto.getOrderNo());
            productSpec.setMeta(productSpecCreateDto.getMeta());
            productSpec.setProduct(product);
            return productSpec;
        }).collect(Collectors.toList()));

        Product newProduct = productRepository.save(product);
        productProducer.produce(TOPIC_PRODUCT_CREATE, newProduct);
        return newProduct;
    }

    /**
     * Update Product
     *
     * @param id
     * @param productUpdateDto
     * @param adminId
     * @return
     */
    public Product update(Long id, ProductUpdateDto productUpdateDto, Long adminId) {
        Product product = findById(id);
        product.setUpdatedBy(adminId);
        if (productUpdateDto.getName() != null) {
            product.setName(productUpdateDto.getName());
        }

        if (productUpdateDto.getCoverMedia() != null) {
            product.setCoverMedia(productUpdateDto.getCoverMedia());
        }

        if (productUpdateDto.getDetailMedia() != null) {
            product.setDetailMedia(productUpdateDto.getDetailMedia());
        }

        if (productUpdateDto.getMeta() != null) {
            product.setMeta(productUpdateDto.getMeta());
        }

        if (productUpdateDto.getStatus() != null) {
            product.setStatus(productUpdateDto.getStatus());
        }

        if (productUpdateDto.getDescription() != null) {
            product.setDescription(productUpdateDto.getDescription());
        }

        if (productUpdateDto.getOrderNo() != null) {
            product.setOrderNo(productUpdateDto.getOrderNo());
        }

        if (productUpdateDto.getSpecs() != null) {
            product.setSpecs(productUpdateDto.getSpecs().stream().map(productSpecUpdateDto -> {
                ProductSpec productSpec = new ProductSpec();
                productSpec.setName(productSpecUpdateDto.getName());
                productSpec.setPrice(productSpecUpdateDto.getPrice());
                productSpec.setCoin(productSpecUpdateDto.getCoin());
                productSpec.setOrgPrice(productSpecUpdateDto.getOrgPrice());
                productSpec.setCount(productSpecUpdateDto.getCount());
                productSpec.setOrderNo(productSpecUpdateDto.getOrderNo());
                productSpec.setMeta(productSpecUpdateDto.getMeta());
                productSpec.setProduct(product);
                return productSpec;
            }).collect(Collectors.toList()));
        }

        Product newProduct = productRepository.save(product);
        productProducer.produce(TOPIC_PRODUCT_UPDATE, newProduct);
        return newProduct;
    }

    /**
     * Delete Product By id
     *
     * @param id
     */
    public void delete(Long id) {
        Product product = findById(id);
        productProducer.produce(TOPIC_PRODUCT_DELETE, id);
        productRepository.delete(product);
    }

    public void orderCreated(ProductOrderCreateDto productOrderCreateDto) {
        if (productOrderCreateDto.getProducts() != null) {
            productOrderCreateDto.getProducts().forEach(productOrderCreateProductDto -> {
                try {
                    // Discount product spec counts
                    ProductSpec productSpec = productSpecRepository.findById(productOrderCreateProductDto.getProductSpecId()).get();
                    productSpec.setCount(productSpec.getCount() - productOrderCreateProductDto.getQuantity());
                    productSpecRepository.save(productSpec);

                    // Send Message To Kafka
                    Product product = productRepository.findById(productOrderCreateProductDto.getProductId()).get();
                    product.setSoldCount(product.getSoldCount() + productOrderCreateProductDto.getQuantity());
                    productRepository.save(product);
                    
                    productProducer.produce(TOPIC_PRODUCT_UPDATE, product);
                } catch (Exception ex) {
                    System.out.println("Could not discount product count: " + ex.getMessage());
                }
            });
        }
    }
}

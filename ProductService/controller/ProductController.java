package com.amrok.product.command.controllers;

import com.amrok.product.command.dto.ProductCreateDto;
import com.amrok.product.command.dto.ProductUpdateDto;
import com.amrok.product.command.models.Product;
import com.amrok.product.command.services.ProductService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/products/")
public class ProductController {
    @Autowired
    private ProductService productService;

    @GetMapping("/")
    @ApiOperation(value = "Get All Products")
    public ResponseEntity<Page<Product>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable paging = PageRequest.of(page, size);
        return ResponseEntity.ok(productService.findAll(paging));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "Get Product by Id")
    public ResponseEntity<Product> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping("/add")
    @ApiOperation(value = "Create Product")
    @ApiImplicitParam(name = "adminId", value = "Admin Id", required = true, allowEmptyValue = false, paramType = "header", dataTypeClass = String.class, example = "1")
    public ResponseEntity<Product> create(@RequestHeader(value = "adminId") String adminId, @Valid @RequestBody ProductCreateDto productCreateDto) {
        return ResponseEntity.ok(productService.create(productCreateDto, Long.valueOf(adminId)));
    }

    @PutMapping("/{id}")
    @ApiOperation(value = "Update Product")
    @ApiImplicitParam(name = "adminId", value = "Admin Id", required = true, allowEmptyValue = false, paramType = "header", dataTypeClass = String.class, example = "1")
    public ResponseEntity<Product> update(@RequestHeader(value = "adminId") String adminId, @PathVariable Long id, @Valid @RequestBody ProductUpdateDto productUpdateDto) {
        return ResponseEntity.ok(productService.update(id, productUpdateDto, Long.valueOf(adminId)));
    }

    @DeleteMapping("/{id}")
    @ApiOperation(value = "Delete Product")
    public ResponseEntity<Long> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(id);
    }
}

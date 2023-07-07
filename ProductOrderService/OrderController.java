package com.amrok.order.controller;

import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.amrok.order.dto.*;
import com.amrok.order.model.OrderModel;
import com.amrok.order.repository.OrderRepository;
import com.amrok.order.service.OrderService;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/v1/product-order-query")
public class OrderController {
    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderService orderService;

    @GetMapping("")
    @ApiOperation(value = "Get All productOrders")
    public PaginationResDto<OrderModel> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "createdAt")  String sortBy) {
        return orderService.findAll(page, size, sortBy);
    }

    @PostMapping("/")
    @ApiOperation(value = "Get All productOrders With Filters")
    public PaginationResDto list(@Validated @RequestBody FilterDto filterDto) {
        return orderService.get(filterDto);
    }

    @GetMapping("/{orderId}")
    @ApiOperation(value = "Get a productOrders by ID")
    public ResponseEntity<OrderModel> getByOrderId(@PathVariable String orderId) {
        try {
            Optional<OrderModel> order = orderRepository.findByOrderId(orderId);
            if (order.isPresent()) {
                return new ResponseEntity<OrderModel>(order.get(), HttpStatus.OK);
            }
            return new ResponseEntity<OrderModel>(HttpStatus.NOT_FOUND);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<OrderModel>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/worker/{workerId}")
    @ApiOperation(value = "Get a productOrders by WorkerID")
    public ResponseEntity<PaginationResDto<OrderModel>> getByWorkerId(
        @PathVariable long workerId,
        @RequestParam(required = false) String[] statuses,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        try {
            System.out.println("Find by Worker: ID = " + workerId);
            PaginationResDto<OrderModel> res = orderService.findByWorker(workerId, statuses, page, size, sortBy);
            return new ResponseEntity<PaginationResDto<OrderModel>>(res, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/user-stats")
    @ApiOperation(value = "Get statistics by user")
    public ResponseEntity<List<Object>> getUserStatistics(
            @RequestParam(required = true) Long userId,
            @RequestParam(required = false) Long districtId
    ) {
        try {
            List<Object> res = orderService.getUserStatistics(userId, districtId);
            return new ResponseEntity<List<Object>>(res, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/user/{userId}")
    @ApiOperation(value = "Get a productOrders by UserId")
    public ResponseEntity<PaginationResDto<OrderModel>> getByUserId(
        @PathVariable long userId,
        @RequestParam(required = false) String[] statuses,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "") String keyword
    ) {
        try {
            System.out.println("Find by User: ID = " + userId);
            List<OrderModel> order = orderRepository.findByUserId(userId);
            PaginationResDto<OrderModel> res = orderService.findByUser(userId, statuses, page, size, sortBy, keyword);
            return new ResponseEntity<PaginationResDto<OrderModel>>(res, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/dashboard")
    @ApiOperation(value = "Get statistics for dashboard page")
    public ResponseEntity<DashboardStatisticsDto> getStatistics(
        @RequestParam(required = true) Long districtId,
        @RequestParam(required = true) Integer year
    ) {
        try {
            DashboardStatisticsDto res = orderService.getStatisticsForDashboard(districtId, year);
            return new ResponseEntity<DashboardStatisticsDto>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/completed-orders")
    @ApiOperation(value = "Get statistics of completed orders")
    public ResponseEntity<List<Object>> getCompletedOrders(
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Date from,
            @RequestParam(required = false) Date to,
            @RequestParam(required = true, defaultValue = "0") Long displayMode,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        try {
            List<Object> res = orderService.getStatisticsForCompletedOrders(
                districtId,
                from,
                to,
                displayMode,
                sortBy,
                sortDirection
            );
            return new ResponseEntity<List<Object>>(res, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println(e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/workers")
    @ApiOperation(value = "Get workers to participate in recycle order process by filter ")
    public ResponseEntity<List<Object>> getWorkers(
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Date from,
            @RequestParam(required = false) Date to,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        try {
            List<Object> res = orderService.getWorkers(
                    districtId,
                    from,
                    to,
                    department,
                    role,
                    name,
                    phone,
                    sortBy,
                    sortDirection
            );
            return new ResponseEntity<List<Object>>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
    @GetMapping("/sell-statistics")
    @ApiOperation(value = "Get workers to participate in recycle order process by filter ")
    public ResponseEntity<List<Object>> getSellStatistics(
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Date from,
            @RequestParam(required = false) Date to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long kindId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long specId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection
    ) {
        try {
            List<Object> res = orderService.getSellStatistics(
                    districtId,
                    from,
                    to,
                    categoryId,
                    kindId,
                    productId,
                    specId,
                    sortBy,
                    sortDirection
            );
            return new ResponseEntity<List<Object>>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/count-by-status")
    @ApiParam(value = "Get counts by status to display badge")
    public ResponseEntity<List<Object>> getCountByStatus(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long workerId,
            @RequestParam(required = false) Long districtId
    ) {
        try {
            List<Object> res = orderService.getCountByStatus(
                    districtId,
                    userId,
                    workerId
            );
            return new ResponseEntity<List<Object>>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/super-admin/dashboard")
    @ApiOperation(value = "Get statistics for super-admin dashboard page")
    public ResponseEntity<SuperAdminDashboardStatisticsDto> getSuperAdminDashboard() {
        try {
            SuperAdminDashboardStatisticsDto res = orderService.getStatisticsForSuperAdminDashboard();
            return new ResponseEntity<SuperAdminDashboardStatisticsDto>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/super-admin/customer-data")
    @ApiOperation(value = "Get recycle's statistics by district")
    public ResponseEntity<List<Object>> getCustomerData(
            @RequestParam(required=false) Long districtId,
            @RequestParam(required = false) Date from,
            @RequestParam(required = false) Date to
    ) {
        try {
            List<Object> res = orderService.getCustomerData(districtId, from, to);
            return new ResponseEntity<List<Object>>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }


    @GetMapping("/check-contents")
    @ApiOperation(value = "Get recycle's statistics by district")
    public ResponseEntity<Boolean> checkContents() {
        try {
            Boolean res = orderService.checkContents();
            return new ResponseEntity<Boolean>(res, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
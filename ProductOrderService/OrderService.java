package com.amrok.order.service;

import com.amrok.order.config.Constant;
import com.amrok.order.dto.*;
import com.amrok.order.feign.address.AddressService;
import com.amrok.order.feign.product.ProductService;
import com.amrok.order.feign.district.DistrictService;
import com.amrok.order.feign.user.UserService;
import com.amrok.order.feign.worker.WorkerFeignService;
import com.amrok.order.kafka.KafkaProducer;
import com.amrok.order.model.OrderModel;
import com.amrok.order.model.ProductInfoModel;
import com.amrok.order.repository.OrderRepository;
import com.amrok.order.repository.ProductInfoRepository;
import com.mongodb.BasicDBObject;
import org.bson.Document;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService implements IOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductInfoRepository productInfoRepository;

    @Autowired
    private WorkerFeignService workerFeignService;

    @Autowired
    private AddressService addressFeignService;

    @Autowired
    private ProductService productFeignService;

    @Autowired
    private UserService userFeignService;

    @Autowired
    private DistrictService districtFeignService;

    @Autowired
    private Constant constant;

    @Autowired
    private ModelMapper mapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    KafkaProducer kafkaProducer;

    @Value("${topic.wechat.template_msg}")
    private String TOPIC_WECHAT_TEMPLATE_MSG;

    @Override
    public void create(CreateOrderDto createOrderDto) {
        long userId = createOrderDto.getUserId();
        long districtId = createOrderDto.getDistrictId();
        long contactAddressId = createOrderDto.getContactAddressId();
        UserDto userDto = userFeignService.getUserById(userId);
        AddressDto addressDto = addressFeignService.getAddressById(contactAddressId);

        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        OrderModel order = mapper.map(createOrderDto, OrderModel.class);
        order.setOrderId(createOrderDto.getOrderId());
        if (createOrderDto.getWechatTransactionId() != null) {
            order.setStatus(Constant.ORDER_STATUS_PAID);
            order.setPaidAt(createOrderDto.getPaidAt());
        } else {
            order.setStatus(Constant.ORDER_STATUS_UNPAID);
        }

        order.setTotalSellPrice(createOrderDto.getTotalSellPrice());
        order.setTotalOrgPrice(createOrderDto.getTotalOrgPrice());
        order.setTotalQuantity(createOrderDto.getTotalQuantity());
        order.setTotalCoin(createOrderDto.getTotalCoin());

        order.setMetaData(createOrderDto.getMetaData());

        order.setUserWechatId(userDto.getWechatId());
        order.setUserName(userDto.getName());
        order.setUserProfileImg(userDto.getAvatar());
        order.setUserGender(userDto.getGender());
        order.setUserPhone(userDto.getPhone());

        order.setDistrictId(addressDto.getDistrictId());
        order.setDistrictName(addressDto.getDistrictName());

        order.setContactName(addressDto.getName());
        order.setContactPhone(addressDto.getPhone());
        order.setContactDistrictId(addressDto.getDistrictId());
        order.setContactAddress(addressDto.getAddress());

        String contents = "";
        if (addressDto.getDistrictId() != districtId) {
            DistrictDto districtDto = districtFeignService.getDistrcitById(addressDto.getDistrictId());
            order.setContactDistrictName(districtDto.getName());
        } else {
            order.setContactDistrictName(addressDto.getDistrictName());
        }

        List<ProductInfoModel> products = new ArrayList<>();
        for (Product product : createOrderDto.getProducts()) {
            long specId = product.getProductSpecId();
            ProductInfoModel productInfoModel = new ProductInfoModel();
            ProductSpecDto productSpecDto = productFeignService.getProductSpecById(specId);

            productInfoModel.setQuantity(product.getQuantity());

            productInfoModel.setDistrictId(createOrderDto.getDistrictId());
            productInfoModel.setDistrictName(userDto.getDistrictName());

            productInfoModel.setCategoryId(productSpecDto.getProduct().getCategoryId());
            productInfoModel.setKindId(productSpecDto.getProduct().getKindId());

            productInfoModel.setProductId(product.getProductId());
            productInfoModel.setProductAdminId(productSpecDto.getProduct().getCreatedById());
            productInfoModel.setProductName(productSpecDto.getProduct().getName());
            productInfoModel.setProductDescription(productSpecDto.getProduct().getDescription());
            productInfoModel.setProductOrderNo(productSpecDto.getOrderNo());
            productInfoModel.setProductCreatedAt(productSpecDto.getCreatedAt());
            productInfoModel.setProductMeta(productSpecDto.getMeta());
            productInfoModel.setProductCoverMedia(productSpecDto.getProduct().getCoverMedia());
            productInfoModel.setProductDetailMedia(productSpecDto.getProduct().getDetailMedia());

            productInfoModel.setSpecId(product.getProductSpecId());
            productInfoModel.setSpecName(productSpecDto.getName());
            productInfoModel.setSpecOrgPrice(productSpecDto.getOrgPrice());
            productInfoModel.setSpecSellPrice(productSpecDto.getPrice());
            productInfoModel.setSpecOrderNo(productSpecDto.getOrderNo());
            productInfoModel.setSpecCoin(productSpecDto.getCoin());

            productInfoRepository.save(productInfoModel);
            products.add(productInfoModel);

            contents = contents.concat(" " + productSpecDto.getProduct().getName())
                    .concat(" " + productSpecDto.getName());
        }
        order.setContents(contents);
        order.setProducts(products);
        orderRepository.save(order);
    }

    @Override
    public void update(UpdateOrderDto updateOrderDto) {
        Optional<OrderModel> orderOpt = Optional.empty();
        if (updateOrderDto.getOrderId() != null) {
            orderOpt = orderRepository.findByOrderId(updateOrderDto.getOrderId());
        } else if (updateOrderDto.getOutTradeNo() != null) {
            orderOpt = orderRepository.findByOutTradeNo(updateOrderDto.getOutTradeNo());
        }
        if (orderOpt.isPresent()) {
            OrderModel order = orderOpt.get();
            String status = updateOrderDto.getStatus();
            Long workerId = updateOrderDto.getWorkerId();
            Date updatedAt = updateOrderDto.getUpdatedAt() == null ? new Date() : updateOrderDto.getUpdatedAt();
            String wechatTransactionId = updateOrderDto.getWechatTransactionId();
            if (workerId != null) {
                WorkerDto workerDto = workerFeignService.getWorker(workerId);
                order.setWorkerId(workerDto.getId());
                order.setWorkerName(workerDto.getName());
                order.setWorkerDepartment(workerDto.getDepartment());
                order.setWorkerGender(workerDto.getGender());
                order.setWorkerBirthday(workerDto.getBirthday());
                order.setWorkerPhone(workerDto.getPhone());
                order.setWorkerEmail(workerDto.getEmail());
                order.setWorkerRoleId(workerDto.getRoleId());
                order.setWorkerRoleName(workerDto.getRoleName());
                order.setWorkerStatus(workerDto.getStatus());
                order.setWorkerNickName(workerDto.getNickName());
                order.setWorkerAvatarUrl(workerDto.getAvatarUrl());
                order.setWorkerWechatId(workerDto.getWechatId());
                status = Constant.ORDER_STATUS_PROCESSING;
            }
            if (wechatTransactionId != null) {
                order.setWechatTransactionId(wechatTransactionId);
                status = Constant.ORDER_STATUS_PAID;
            }
            if (status != null) {
                order.setStatus(status);
                switch (status) {
                    case Constant.ORDER_STATUS_PAID:
                        order.setPaidAt(updatedAt);
                        break;
                    case Constant.ORDER_STATUS_PROCESSING:
                        order.setAllocatedAt(updatedAt);
                        break;
                    case Constant.ORDER_STATUS_SHIPPED:
                        order.setWorkerConfirmedAt(updatedAt);
                        break;
                    case Constant.ORDER_STATUS_COMPLETED:
                        order.setUserConfirmedAt(updatedAt);
                        break;
                    case Constant.ORDER_STATUS_CANCELLED:
                        order.setCancelledAt(updatedAt);
                        break;
                    default:
                        break;
                }
            }
            order.setModifiedAt(updatedAt);
            orderRepository.save(order);
        }
    }

    @Override
    public PaginationResDto get(FilterDto filterDto) {
        PaginationResDto<OrderModel> res = new PaginationResDto<OrderModel>();
        Query query = new Query();

        if (filterDto.getOrderId() != null && !filterDto.getOrderId().isEmpty()) {
            query.addCriteria(Criteria.where("orderId").regex(filterDto.getOrderId()));
        }
        if (filterDto.getWechatTransactionId() != null && !filterDto.getWechatTransactionId().isEmpty()) {
            query.addCriteria(Criteria.where("wechatTransactionId").regex(filterDto.getWechatTransactionId()));
        }
        if (filterDto.getStatus() != null) {
            query.addCriteria(Criteria.where("status").in(filterDto.getStatus()));
        }

        if (filterDto.getContactName() != null && !filterDto.getContactName().isEmpty()) {
            query.addCriteria(
                    Criteria.where("").orOperator(Criteria.where("contactName").regex(filterDto.getContactName()),
                            Criteria.where("contactPhone").regex(filterDto.getContactName())));
        }

        if (filterDto.getContactPhone() != null && !filterDto.getContactPhone().isEmpty()) {
            query.addCriteria(Criteria.where("contactPhone").regex(filterDto.getContactPhone()));
        }
        if (filterDto.getContactDistrictName() != null && !filterDto.getContactDistrictName().isEmpty()) {
            query.addCriteria(Criteria.where("contactDistrictName").regex(filterDto.getContactDistrictName()));
        }

        if (filterDto.getContactAddress() != null && !filterDto.getContactAddress().isEmpty()) {
            query.addCriteria(
                    Criteria.where("").orOperator(Criteria.where("contactAddress").regex(filterDto.getContactAddress()),
                            Criteria.where("contactDistrictName").regex(filterDto.getContactAddress())));
        }

        if (filterDto.getDistrictId() != null) {
            query.addCriteria(Criteria.where("districtId").is(filterDto.getDistrictId()));
        }
        if (filterDto.getWorkerId() != null) {
            System.out.println(filterDto.getWorkerId());
            query.addCriteria(Criteria.where("workerId").is(filterDto.getWorkerId()));
        }
        if (filterDto.getPaidAtFrom() != null && filterDto.getPaidAtTo() != null) {
            query.addCriteria(new Criteria().andOperator(
                    Criteria.where("paidAt").gte(filterDto.getPaidAtFrom()),
                    Criteria.where("paidAt").lte(filterDto.getPaidAtTo())));
        }

        String sortBy = filterDto.getSortBy() != null ? filterDto.getSortBy() : "createdAt";
        Sort.Direction direction = (filterDto.getSortDirection() == null || filterDto.getSortDirection().matches("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        query.with(Sort.by(direction, sortBy));

        if (filterDto.getPage() != null) {
            Integer pageSize = filterDto.getPageSize() != null ? filterDto.getPageSize() : 10;
            Pageable pageableRequest = PageRequest.of(filterDto.getPage(), pageSize);
            query.with(pageableRequest);
            res.setPage(filterDto.getPage());
            res.setOffset(filterDto.getPage() * pageSize);
        }

        List<OrderModel> orders = mongoTemplate.find(query, OrderModel.class);
        res.setData(orders);
        long count = mongoTemplate.count(query.skip(-1).limit(-1), OrderModel.class);
        res.setTotal(count);
        return res;
    }

    @Override
    public PaginationResDto findAll(int page, int size, String sortBy) {
        PaginationResDto<OrderModel> res = new PaginationResDto<OrderModel>();
        Pageable paging = PageRequest.of(page, size);
        Query query = new Query();
        query.with(paging);
        query.with(Sort.by(Sort.Direction.DESC, sortBy));
        res.setData(mongoTemplate.find(query, OrderModel.class));
        long count = mongoTemplate.count(query.skip(-1).limit(-1), OrderModel.class);
        res.setTotal(count);
        res.setPage(page);
        res.setOffset(page * size);
        return res;
    }

    @Override
    public PaginationResDto findByWorker(long workerId, String[] statuses, int page, int size, String sortBy) {
        PaginationResDto<OrderModel> res = new PaginationResDto<OrderModel>();
        Pageable paging = PageRequest.of(page, size);
        Query query = new Query();
        if (statuses != null && statuses.length > 0) {
            query.addCriteria(Criteria.where("status").in(statuses));
        }
        query.addCriteria(Criteria.where("workerId").is(workerId));
        query.with(paging);
        query.with(Sort.by(Sort.Direction.DESC, sortBy));

        res.setData(mongoTemplate.find(query, OrderModel.class));
        long count = mongoTemplate.count(query.skip(-1).limit(-1), OrderModel.class);
        res.setTotal(count);
        res.setPage(page);
        res.setOffset(page * size);
        return res;
    }

    @Override
    public PaginationResDto findByUser(long userId, String[] statuses, int page, int size, String sortBy,
            String keyword) {
        PaginationResDto<OrderModel> res = new PaginationResDto<OrderModel>();
        Pageable paging = PageRequest.of(page, size);
        Query query = new Query();
        if (statuses != null && statuses.length > 0) {
            query.addCriteria(Criteria.where("status").in(statuses));
        }
        if (!keyword.isEmpty()) {
            query.addCriteria(Criteria.where("contents").regex(keyword));
        }
        query.addCriteria(Criteria.where("userId").is(userId));
        query.with(paging);
        query.with(Sort.by(Sort.Direction.DESC, sortBy));

        res.setData(mongoTemplate.find(query, OrderModel.class));
        long count = mongoTemplate.count(query.skip(-1).limit(-1), OrderModel.class);
        res.setTotal(count);
        res.setPage(page);
        res.setOffset(page * size);
        return res;
    }

    public List<Object> getUserStatistics(Long userId, Long districtId) {
        List<AggregationOperation> aggregationList = new ArrayList<AggregationOperation>();

        aggregationList.add(Aggregation.match(new Criteria("userId").is(userId)));
        if (districtId != null)
            aggregationList.add(Aggregation.match(new Criteria("districtId").is(districtId)));
        aggregationList.add(Aggregation.project("userId", "totalSellPrice", "totalCoin"));

        GroupOperation groupOperation = Aggregation.group("userId")
                .count().as("totalCount")
                .sum("totalSellPrice").as("totalSellPrice")
                .sum("totalCoin").as("totalCoin");
        aggregationList.add(groupOperation);
        aggregationList.add(Aggregation.project("totalCount", "totalSellPrice", "totalCoin"));

        Aggregation aggregation = Aggregation.newAggregation(aggregationList);
        AggregationResults<Object> aggregationResults = mongoTemplate.aggregate(aggregation, "product-orders",
                Object.class);
        return aggregationResults.getMappedResults();
    }

    @Override
    public DashboardStatisticsDto getStatisticsForDashboard(Long districtId, Integer year) {
        DashboardStatisticsDto res = new DashboardStatisticsDto();
        Calendar minDate = Calendar.getInstance();
        minDate.set(Calendar.YEAR, year);
        minDate.set(Calendar.MONTH, Calendar.JANUARY);
        minDate.set(Calendar.DATE, 1);
        minDate.set(Calendar.HOUR, 0);
        minDate.set(Calendar.MINUTE, 0);
        minDate.set(Calendar.SECOND, 0);

        Calendar maxDate = Calendar.getInstance();
        maxDate.set(Calendar.YEAR, year);
        maxDate.set(Calendar.MONTH, Calendar.DECEMBER);
        maxDate.set(Calendar.DATE, 31);
        maxDate.set(Calendar.HOUR, 23);
        maxDate.set(Calendar.MINUTE, 59);
        maxDate.set(Calendar.SECOND, 59);

        Calendar thisMonth = Calendar.getInstance();
        thisMonth.set(Calendar.DATE, 1);
        thisMonth.set(Calendar.HOUR, 0);
        thisMonth.set(Calendar.MINUTE, 0);
        thisMonth.set(Calendar.SECOND, 0);
        System.out.println("=== THIS MONTH ===");
        System.out.println(thisMonth.getTime());

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        System.out.println("=== TODAY ===");
        System.out.println(today.getTime());

        // Get this month orders
        Query query = new Query();
        query.addCriteria(Criteria.where("districtId").is(districtId));
        query.addCriteria(Criteria.where("createdAt").gte(thisMonth.getTime()));
        long count = mongoTemplate.count(query.skip(-1).limit(-1), OrderModel.class);
        res.setThisMonthOrders(count);

        // get daily orders
        List<AggregationOperation> aggregationList1 = new ArrayList<AggregationOperation>();
        aggregationList1.add(Aggregation.match(new Criteria("districtId").is(districtId)));
        aggregationList1.add(Aggregation.match(new Criteria("createdAt").gte(today.getTime())));
        aggregationList1.add(Aggregation.group("status").count().as("totalCount"));
        aggregationList1.add(Aggregation.project("totalCount").and("status").previousOperation());
        Aggregation aggregations1 = Aggregation.newAggregation(aggregationList1);
        AggregationResults<Object> aggregationResult1 = mongoTemplate.aggregate(aggregations1, "product-orders",
                Object.class);
        res.setDailyOrders(aggregationResult1.getMappedResults());

        // Get orders count by status
        List<AggregationOperation> aggregationList2 = new ArrayList<AggregationOperation>();
        aggregationList2.add(Aggregation.match(new Criteria("districtId").is(districtId)));
        aggregationList2.add(Aggregation.group("status").count().as("totalCount"));
        aggregationList2.add(Aggregation.project("totalCount").and("status").previousOperation());
        Aggregation aggregations2 = Aggregation.newAggregation(aggregationList2);
        AggregationResults<Object> aggregationResult2 = mongoTemplate.aggregate(aggregations2, "product-orders",
                Object.class);
        res.setOrderCountByStatus(aggregationResult2.getMappedResults());

        // Get statistics by months
        List<AggregationOperation> aggregationList3 = new ArrayList<AggregationOperation>();
        aggregationList3.add(Aggregation
                .match(new Criteria("districtId").is(districtId)));
        aggregationList3.add(Aggregation
                .match(new Criteria().andOperator(
                        Criteria.where("userConfirmedAt").gte(minDate.getTime()),
                        Criteria.where("userConfirmedAt").lte(maxDate.getTime()))));
        aggregationList3.add(Aggregation
                .match(new Criteria("status").is("completed")));
        aggregationList3.add(Aggregation.project()
                .and(DateOperators.Month.monthOf("userConfirmedAt")).as("month"));
        aggregationList3.add(Aggregation.group("month")
                .count().as("totalCount"));

        Aggregation aggregation3 = Aggregation.newAggregation(aggregationList3);
        AggregationResults<Object> aggregationResults3 = mongoTemplate.aggregate(aggregation3, "product-orders",
                Object.class);
        res.setMonthlyOrders(aggregationResults3.getMappedResults());

        return res;
    }

    @Override
    public List<Object> getStatisticsForCompletedOrders(Long districtId, Date from, Date to, Long displayMode,
            String sortBy, String sortDirection) {
        List<AggregationOperation> aggregationList = new ArrayList<AggregationOperation>();

        // Get statistics by months
        if (districtId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("districtId").is(districtId));
            aggregationList.add(operation);
        }

        aggregationList.add(Aggregation.match(new Criteria("status").is("completed")));

        if (from != null && to != null) {
            Calendar minDate = Calendar.getInstance();
            minDate.setTime(from);
            minDate.set(Calendar.HOUR, 0);
            minDate.set(Calendar.MINUTE, 0);
            minDate.set(Calendar.SECOND, 0);
            Calendar maxDate = Calendar.getInstance();
            maxDate.setTime(to);
            maxDate.set(Calendar.HOUR, 23);
            maxDate.set(Calendar.MINUTE, 59);
            maxDate.set(Calendar.SECOND, 59);
            aggregationList.add(
                    Aggregation.match(new Criteria().andOperator(
                            Criteria.where("userConfirmedAt").gte(minDate.getTime()),
                            Criteria.where("userConfirmedAt").lte(maxDate.getTime()))));
        }

        aggregationList
                .add(Aggregation.project("totalSellPrice", "totalOrgPrice", "totalCoin", "totalQuantity", "districtId")
                        .and(DateOperators.Year.yearOf("userConfirmedAt")).as("year")
                        .and(DateOperators.Month.monthOf("userConfirmedAt")).as("month")
                        .and(DateOperators.DayOfMonth.dayOfMonth("userConfirmedAt")).as("date"));
        if (displayMode == 1) { // monthly
            GroupOperation groupOperation = Aggregation.group("year", "month", "districtId")
                    .count().as("totalCount")
                    .sum("totalSellPrice").as("totalSellPrice")
                    .sum("totalOrgPrice").as("totalOrgPrice")
                    .sum("totalCoin").as("totalCoin")
                    .sum("totalQuantity").as("totalQuantity");
            aggregationList.add(groupOperation);
        } else if (displayMode == 2) { // yearly
            GroupOperation groupOperation = Aggregation.group("year", "districtId")
                    .count().as("totalCount")
                    .sum("totalSellPrice").as("totalSellPrice")
                    .sum("totalOrgPrice").as("totalOrgPrice")
                    .sum("totalCoin").as("totalCoin")
                    .sum("totalQuantity").as("totalQuantity");
            aggregationList.add(groupOperation);
        } else { // daily
            GroupOperation groupOperation = Aggregation.group("year", "month", "date", "districtId")
                    .count().as("totalCount")
                    .sum("totalSellPrice").as("totalSellPrice")
                    .sum("totalOrgPrice").as("totalOrgPrice")
                    .sum("totalCoin").as("totalCoin")
                    .sum("totalQuantity").as("totalQuantity");
            aggregationList.add(groupOperation);
        }

        String sortBy_ = sortBy != null ? sortBy : "totalCount";
        Sort.Direction sortDirection_ = (sortDirection == null || sortDirection.equals("asc")) ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        SortOperation sortOperation = Aggregation.sort(Sort.by(sortDirection_, sortBy_));
        aggregationList.add(sortOperation);

        Aggregation aggregation = Aggregation.newAggregation(aggregationList);
        AggregationResults<Object> aggregationResults = mongoTemplate.aggregate(aggregation, "product-orders",
                Object.class);

        return aggregationResults.getMappedResults();
    }

    @Override
    public List<Object> getWorkers(
            Long districtId,
            Date from,
            Date to,
            String department,
            Integer role,
            String name,
            String phone,
            String sortBy,
            String sortDirection) {
        List<AggregationOperation> aggregationList = new ArrayList<AggregationOperation>();

        if (districtId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("districtId").is(districtId));
            aggregationList.add(operation);
        }

        aggregationList.add(Aggregation.project(
                "workerId",
                "workerName",
                "workerDepartment",
                "workerPhone",
                "workerRoleId",
                "workerRoleName",
                "userConfirmedAt",
                "status"));
        aggregationList.add(Aggregation.match(new Criteria("status").is("completed")));

        if (from != null && to != null) {
            Calendar minDate = Calendar.getInstance();
            minDate.setTime(from);
            minDate.set(Calendar.HOUR, 0);
            minDate.set(Calendar.MINUTE, 0);
            minDate.set(Calendar.SECOND, 0);
            Calendar maxDate = Calendar.getInstance();
            maxDate.setTime(to);
            maxDate.set(Calendar.HOUR, 23);
            maxDate.set(Calendar.MINUTE, 59);
            maxDate.set(Calendar.SECOND, 59);
            aggregationList.add(
                    Aggregation.match(new Criteria().andOperator(
                            Criteria.where("userConfirmedAt").gte(minDate.getTime()),
                            Criteria.where("userConfirmedAt").lte(maxDate.getTime()))));
        }

        if (department != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("workerDepartment").regex(department));
            aggregationList.add(operation);
        }
        if (role != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("workerRoleId").is(role));
            aggregationList.add(operation);
        }
        if (name != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("workerName").regex(name));
            aggregationList.add(operation);
        }
        if (phone != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("workerPhone").regex(phone));
            aggregationList.add(operation);
        }

        GroupOperation groupOperation = Aggregation.group(
                "workerId",
                "workerName",
                "workerDepartment",
                "workerPhone",
                "workerRoleId").count().as("totalCount");
        aggregationList.add(groupOperation);
        aggregationList.add(Aggregation.project("totalCount"));

        String sortBy_ = sortBy != null ? sortBy : "totalCount";
        Sort.Direction sortDirection_ = (sortDirection == null || sortDirection.equals("asc")) ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        SortOperation sortOperation = Aggregation.sort(Sort.by(sortDirection_, sortBy_));
        aggregationList.add(sortOperation);

        Aggregation aggregation2 = Aggregation.newAggregation(aggregationList);
        AggregationResults<Object> aggregationResults = mongoTemplate.aggregate(aggregation2, "product-orders",
                Object.class);
        return aggregationResults.getMappedResults();
    }

    public List<Object> getSellStatistics(
            Long districtId,
            Date from,
            Date to,
            Long categoryId,
            Long kindId,
            Long productId,
            Long specId,
            String sortBy,
            String sortDirection) {
        List<AggregationOperation> aggregationList = new ArrayList<AggregationOperation>();

        if (districtId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("districtId").is(districtId));
            aggregationList.add(operation);
        }
        aggregationList.add(Aggregation.match(new Criteria("status").is("completed")));
        String query = "{'$project':{'paidAt':1,'districtId':1,'products_fk':{'$map':{'input':{'$map':{'input':'$products','in':{'$arrayElemAt':[{'$objectToArray':'$$this'},1]}}},'in':'$$this.v'}}}}";
        aggregationList.add(new CustomAggregationOperation(query));
        aggregationList.add(Aggregation.lookup("product-infos", "products_fk", "_id", "productInfo"));
        aggregationList.add(Aggregation.unwind("productInfo"));

        aggregationList.add(Aggregation.project(
                "districtId",
                "paidAt")
                .and("productInfo.specId").as("specId")
                .and("productInfo.specName").as("specName")
                .and("productInfo.productId").as("productId")
                .and("productInfo.productName").as("productName")
                .and("productInfo.categoryId").as("categoryId")
                .and("productInfo.kindId").as("kindId")
                .and("productInfo.quantity").as("specQuantity")
                .andExpression("productInfo.quantity * productInfo.specOrgPrice").as("specOrgPrice")
                .andExpression("productInfo.quantity * productInfo.specSellPrice").as("specSellPrice")
                .andExpression("productInfo.quantity * productInfo.specCoin").as("specCoin"));

        if (from != null && to != null) {
            Calendar minDate = Calendar.getInstance();
            minDate.setTime(from);
            minDate.set(Calendar.HOUR, 0);
            minDate.set(Calendar.MINUTE, 0);
            minDate.set(Calendar.SECOND, 0);
            Calendar maxDate = Calendar.getInstance();
            maxDate.setTime(to);
            maxDate.set(Calendar.HOUR, 23);
            maxDate.set(Calendar.MINUTE, 59);
            maxDate.set(Calendar.SECOND, 59);
            aggregationList.add(
                    Aggregation.match(new Criteria().andOperator(
                            Criteria.where("paidAt").gte(minDate.getTime()),
                            Criteria.where("paidAt").lte(maxDate.getTime()))));
        }

        if (categoryId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("categoryId").is(categoryId));
            aggregationList.add(operation);
        }
        if (kindId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("kindId").is(kindId));
            aggregationList.add(operation);
        }
        if (productId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("productId").is(productId));
            aggregationList.add(operation);
        }
        if (specId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("specId").is(specId));
            aggregationList.add(operation);
        }
        GroupOperation groupOperation = Aggregation.group(
                "categoryId",
                "kindId",
                "productId",
                "productName",
                "specId",
                "specName").count().as("totalCount")
                .sum("specQuantity").as("totalSpecQuantity")
                .sum("specOrgPrice").as("totalSpecOrgPrice")
                .sum("specSellPrice").as("totalSpecSellPrice")
                .sum("specCoin").as("totalSpecCoin");
        aggregationList.add(groupOperation);
        aggregationList.add(Aggregation.project("totalCount", "totalSpecQuantity", "totalSpecOrgPrice",
                "totalSpecSellPrice", "totalSpecCoin"));

        String sortBy_ = sortBy != null ? sortBy : "totalCount";
        Sort.Direction sortDirection_ = (sortDirection == null || sortDirection.equals("asc")) ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        SortOperation sortOperation = Aggregation.sort(Sort.by(sortDirection_, sortBy_));
        aggregationList.add(sortOperation);

        Aggregation aggregations = Aggregation.newAggregation(aggregationList);
        AggregationResults<Object> aggregationResults = mongoTemplate.aggregate(aggregations, "product-orders",
                Object.class);
        return aggregationResults.getMappedResults();
    }

    @Override
    public SuperAdminDashboardStatisticsDto getStatisticsForSuperAdminDashboard() {
        SuperAdminDashboardStatisticsDto result = new SuperAdminDashboardStatisticsDto();

        Calendar thisMonth = Calendar.getInstance();
        thisMonth.set(Calendar.DATE, 1);
        thisMonth.set(Calendar.HOUR, 0);
        thisMonth.set(Calendar.MINUTE, 0);
        thisMonth.set(Calendar.SECOND, 0);
        System.out.println("=== THIS MONTH ===");
        System.out.println(thisMonth.getTime());

        ////////////////////////////
        List<AggregationOperation> aggregationList1 = new ArrayList<AggregationOperation>();
        aggregationList1.add(Aggregation
                .match(new Criteria("status").is(constant.ORDER_STATUS_COMPLETED)));
        aggregationList1.add(Aggregation
                .match(new Criteria("userConfirmedAt").gte(thisMonth.getTime())));
        aggregationList1.add(Aggregation.project("districtId"));
        aggregationList1.add(Aggregation.group("districtId")
                .count().as("totalOrders"));

        aggregationList1.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalOrders")));
        aggregationList1.add(Aggregation.limit(5));

        Aggregation aggregation1 = Aggregation.newAggregation(aggregationList1);
        AggregationResults<Object> aggregationResults1 = mongoTemplate.aggregate(aggregation1, "product-orders",
                Object.class);
        result.setProductOrdersByCountTop5(aggregationResults1.getMappedResults());

        ////////////////////////////
        List<AggregationOperation> aggregationList = new ArrayList<AggregationOperation>();
        aggregationList.add(Aggregation
                .match(new Criteria("status").is(constant.ORDER_STATUS_COMPLETED)));
        aggregationList.add(Aggregation
                .match(new Criteria("userConfirmedAt").gte(thisMonth.getTime())));
        aggregationList.add(Aggregation.project("districtId").andExpression("totalSellPrice - totalCoin").as("price"));
        aggregationList.add(Aggregation.group("districtId")
                .sum("price").as("totalSellPrice"));

        aggregationList.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalSellPrice")));
        aggregationList.add(Aggregation.limit(5));

        Aggregation aggregation = Aggregation.newAggregation(aggregationList);
        AggregationResults<Object> aggregationResults = mongoTemplate.aggregate(aggregation, "product-orders",
                Object.class);
        result.setProductOrdersByPriceTop5(aggregationResults.getMappedResults());

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        System.out.println("=== TODAY ===");
        System.out.println(today.getTime());

        // Get this month orders
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("status").is(Constant.ORDER_STATUS_COMPLETED));
        long count1 = mongoTemplate.count(query1.skip(-1).limit(-1), OrderModel.class);
        result.setTotalOrders(count1);

        // get daily orders
        Query query2 = new Query();
        query2.addCriteria(Criteria.where("status").is(Constant.ORDER_STATUS_COMPLETED));
        query2.addCriteria(Criteria.where("createdAt").gte(today.getTime()));
        long count2 = mongoTemplate.count(query2.skip(-1).limit(-1), OrderModel.class);
        result.setTodayOrders(count2);

        return result;
    }

    @Override
    public List<Object> getCustomerData(Long districtId, Date from, Date to) {
        List<AggregationOperation> aggregationList = new ArrayList<AggregationOperation>();

        aggregationList.add(Aggregation
                .match(new Criteria("status").is(constant.ORDER_STATUS_COMPLETED)));
        if (districtId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("districtId").is(districtId));
            aggregationList.add(operation);
        }
        if (from != null && to != null) {
            Calendar minDate = Calendar.getInstance();
            minDate.setTime(from);
            minDate.set(Calendar.HOUR, 0);
            minDate.set(Calendar.MINUTE, 0);
            minDate.set(Calendar.SECOND, 0);
            Calendar maxDate = Calendar.getInstance();
            maxDate.setTime(to);
            maxDate.set(Calendar.HOUR, 23);
            maxDate.set(Calendar.MINUTE, 59);
            maxDate.set(Calendar.SECOND, 59);
            aggregationList.add(
                    Aggregation.match(new Criteria().andOperator(
                            Criteria.where("userConfirmedAt").gte(minDate.getTime()),
                            Criteria.where("userConfirmedAt").lte(maxDate.getTime()))));
        }

        aggregationList.add(Aggregation.group("districtId")
                .count().as("totalCount")
                .sum("totalSellPrice").as("totalSellPrice")
                .sum("totalCoin").as("totalCoin"));
        Aggregation aggregation = Aggregation.newAggregation(aggregationList);
        AggregationResults<Object> aggregationResults = mongoTemplate.aggregate(aggregation, "product-orders",
                Object.class);
        return aggregationResults.getMappedResults();
    }

    @Override
    public List<Object> getCountByStatus(
            Long districtId,
            Long userId,
            Long workerId) {
        List<AggregationOperation> aggregationList = new ArrayList<AggregationOperation>();

        if (districtId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("districtId").is(districtId));
            aggregationList.add(operation);
        }
        if (userId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("userId").is(userId));
            aggregationList.add(operation);
        }
        if (workerId != null) {
            MatchOperation operation = Aggregation
                    .match(new Criteria("workerId").is(workerId));
            aggregationList.add(operation);
        }
        aggregationList.add(Aggregation.project("status"));
        aggregationList.add(Aggregation.group("status")
                .count().as("totalCount"));
        Aggregation aggregation = Aggregation.newAggregation(aggregationList);
        AggregationResults<Object> aggregationResults = mongoTemplate.aggregate(aggregation, "product-orders",
                Object.class);
        return aggregationResults.getMappedResults();
    }

    @Override
    public Boolean deleteOrders(DeleteDto deleteDto) {
        try {
            List<String> ids = deleteDto.getIds();
            for (String id : ids) {
                orderRepository.deleteOrderByOrderId(id);
            }
            return true;
        } catch (Exception e) {
            System.out.println(e);
            return false;
        }
    }

    @Override
    public Boolean cancelOrders(DeleteDto deleteDto) {
        try {
            List<String> ids = deleteDto.getIds();
            for (String id : ids) {
                Optional<OrderModel> orderOpt = orderRepository.findByOrderId(id);
                if (orderOpt.isPresent()) {
                    OrderModel order = orderOpt.get();
                    order.setStatus(Constant.ORDER_STATUS_CANCELLED);
                    order.setCancelledAt(new Date());
                    orderRepository.save(order);
                }
            }
            return true;
        } catch (Exception e) {
            System.out.println(e);
            return false;
        }
    }

    @Override
    public Boolean checkContents() {
        List<OrderModel> orders = orderRepository.findAll();
        for (OrderModel order : orders) {
            if (order.getContents() == null) {
                String contents = "";
                for (ProductInfoModel product : order.getProducts()) {
                    contents = contents.concat(" " + product.getProductName()).concat(" " + product.getSpecName());
                }
                order.setContents(contents);
                orderRepository.save(order);
            }
        }
        return true;
    }
}
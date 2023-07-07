package com.amrok.order.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.*;

import java.util.*;

@Data
@Document(collection = "product-orders")
@Accessors(chain = true)
public class OrderModel {
    @MongoId(FieldType.OBJECT_ID)
    private String id;
    private String orderId;
    private String status;
    private String outTradeNo;;
    private String wechatTransactionId;

    private Date createdAt;
    private Date modifiedAt;
    private Date paidAt;
    private Date allocatedAt;
    private Date workerConfirmedAt;
    private Date userConfirmedAt;
    private Date cancelledAt;

    private Double totalCoin;
    private Double totalSellPrice;
    private Double totalOrgPrice;
    private Double totalQuantity;

    private String metaData;

    // user
    private Long userId;
    private String userWechatId;
    private String userName;
    private String userProfileImg;
    private Integer userGender;
    private String userPhone;

    // district
    private Long districtId;
    private String districtName;

    // contact address
    private String contactAddressId;
    private String contactName;
    private String contactPhone;
    private Long contactDistrictId;
    private String contactDistrictName;
    private String contactAddress;

    // worker
    private Long workerId;
    private String workerEmail;
    private String workerName;
    private String workerDepartment;
    private Integer workerStatus;
    private Integer workerGender;
    private Date workerBirthday;
    private String workerPhone;
    private Integer workerRoleId;
    private String workerRoleName;
    private String workerNickName;
    private String workerAvatarUrl;
    private String workerWechatId;

    private String contents;

    @DBRef
    private List<ProductInfoModel> products;

    public OrderModel() {
        this.products = new ArrayList<>();
    }
}
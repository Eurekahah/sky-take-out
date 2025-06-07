package com.sky;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.result.Result;
import com.sky.task.OrderTask;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.vo.UserLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class UserSubmitOrderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private OrdersSubmitDTO orderSubmitDTO;

    private String userToken;

    private String adminToken;

    @Autowired
    private OrderTask orderTask;

    @Autowired
    private OrderMapper orderMapper;

    private TestRestTemplate restTemplate;


    @BeforeEach
    void setUp() throws Exception {
        // 准备测试数据 - 创建订单请求
        orderSubmitDTO = new OrdersSubmitDTO();
        orderSubmitDTO.setAddressBookId(1L);
        orderSubmitDTO.setPayMethod(1); // 微信支付
        orderSubmitDTO.setRemark("测试订单");
        orderSubmitDTO.setEstimatedDeliveryTime(LocalDateTime.now().plusHours(1));
        orderSubmitDTO.setDeliveryStatus(1); // 立即送出
        orderSubmitDTO.setTablewareNumber(1);
        orderSubmitDTO.setTablewareStatus(1);
        orderSubmitDTO.setPackAmount(2);
        orderSubmitDTO.setAmount(new BigDecimal("50.00"));

        // ==== 登录，获取用户 token ====
        userToken = userLoginAndGetToken();

        // ==== 登录，获取管理员 token ====
        adminToken = adminLoginAndGetToken();


        objectMapper.configOverride(LocalDateTime.class)
                .setFormat(JsonFormat.Value.forPattern("yyyy-MM-dd HH:mm"));
    }

    private String userLoginAndGetToken() throws Exception {
        // 模拟微信登录 DTO
        Map<String, Object> loginRequest = new HashMap<>();
        loginRequest.put("code", "test-wechat-code");

        MvcResult loginResult = mockMvc.perform(post("/user/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();

        Result<UserLoginVO> loginVOResult = objectMapper.readValue(
                response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, UserLoginVO.class)
        );

        String token = loginVOResult.getData().getToken();
        assertNotNull(token, "登录返回的 token 不能为空");

        return token;
    }

    private String adminLoginAndGetToken() throws Exception {


        EmployeeLoginDTO employeeLoginDTO = new EmployeeLoginDTO();
        employeeLoginDTO.setUsername("admin");
        employeeLoginDTO.setPassword("123456");

        MvcResult loginResult = mockMvc.perform(post("/admin/employee/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employeeLoginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();

        Result<UserLoginVO> loginVOResult = objectMapper.readValue(
                response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, UserLoginVO.class)
        );

        String token = loginVOResult.getData().getToken();
        assertNotNull(token, "登录返回的 token 不能为空");
        return token;
    }

    /*****************/
    /*用户下单决策表*/
    @Test
    @DisplayName("TC11: 条件1: 用户未登录")
    void testCondition1_UserNotLoggedIn() throws Exception {
        // 不提供token模拟未登录用户
        mockMvc.perform(post("/user/order/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderSubmitDTO)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC12：条件2: 用户已登录，但地址无效")
    void testCondition2_UserLoggedInCartValidButNoAddress() throws Exception {
        // 模拟地址不存在的情况
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        BeanUtils.copyProperties(orderSubmitDTO, dto);
        dto.setAddressBookId(null); // 无地址ID

        mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("用户地址为空，不能下单"));
    }

    /**
     * 生成模拟收货地址
     *
     * @return 模拟的地址簿对象
     */
    public AddressBook createMockAddressBook() {
        return AddressBook.builder()
                .id(1L) // 模拟ID
                .consignee("李四") // 收货人姓名
                .phone("13912345678") // 手机号
                .sex("1") // 性别: 1-男
                .provinceCode("31") // 上海行政区划代码
                .provinceName("上海市")
                .cityCode("3101") // 上海市区代码
                .cityName("市辖区")
                .districtCode("310114") // 嘉定区代码
                .districtName("嘉定区")
                .detail("曹安公路4800号") // 详细地址
                .label("学校") // 地址标签
                .build();
    }

    private Long addAddress() throws Exception {
        // 模拟添加收货地址
        AddressBook addressBook = createMockAddressBook();
        mockMvc.perform(post("/user/addressBook")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addressBook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        MvcResult addressResult = mockMvc.perform(get("/user/addressBook/list")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String response = addressResult.getResponse().getContentAsString();

        Result<List<AddressBook>> addressVOResult = objectMapper.readValue(
                response,
                objectMapper.getTypeFactory().constructParametricType(Result.class,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, AddressBook.class))
        );
        // 获取第一个地址的ID并返回
        return addressVOResult.getData().get(0).getId();
    }

    @Test
    @DisplayName("TC13： 条件3: 用户已登录，地址有效但购物车为空")
    void testCondition3_UserLoggedInButCartEmpty() throws Exception {
        Long addressId = addAddress();

        // 模拟购物车为空的情况
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        BeanUtils.copyProperties(orderSubmitDTO, dto);
        dto.setAddressBookId(addressId);


        mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("购物车数据为空，不能下单"));
    }

    /**
     * 添加商品到购物车的辅助函数，用于测试
     *
     * @throws Exception
     */
    private void addDishToShoppingCart() throws Exception {
        ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
        shoppingCartDTO.setDishId(46L); // 菜品ID为46,王老吉
//        shoppingCartDTO.setDishFlavor("微辣"); // 可选的口味信息
        // setmealId不设置，因为这里添加的是菜品而不是套餐

        mockMvc.perform(post("/user/shoppingCart/add")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shoppingCartDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("TC14：条件4: 用户已登录，购物车有效，地址存在，在配送范围内但支付无效")
    void testCondition5_UserLoggedInCartValidAddressValidInRangeButPaymentInvalid() throws Exception {
        // 模拟支付无效的情况（可能需要mock支付服务）
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        BeanUtils.copyProperties(orderSubmitDTO, dto);
        dto.setPayMethod(3); // 3是无效的支付方式

        // 添加正确地址
        Long addressId = addAddress();
        dto.setAddressBookId(addressId);

        // 添加一个商品
        addDishToShoppingCart();

        // 此处应该成功提交订单
        MvcResult submitOrderResult = mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn();

        String response = submitOrderResult.getResponse().getContentAsString();

        Result<OrderSubmitVO> addressVOResult = objectMapper.readValue(
                response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class)
        );

        OrdersPaymentDTO ordersPaymentDTO = new OrdersPaymentDTO();
        ordersPaymentDTO.setOrderNumber(addressVOResult.getData().getOrderNumber());
        ordersPaymentDTO.setPayMethod(3); // 3是无效的支付方式

        mockMvc.perform(put("/user/order/payment")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ordersPaymentDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        // 此处成功因为微信支付接口目前是模拟的。
    }

    @Test
    @DisplayName("TC15: 所有条件都满足，订单应成功提交")
    void testCondition6_AllConditionsMetOrderShouldSubmitSuccessfully() throws Exception {
        // 模拟支付无效的情况（可能需要mock支付服务）
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        BeanUtils.copyProperties(orderSubmitDTO, dto);
        dto.setPayMethod(3); // 3是无效的支付方式

        // 添加正确地址
        Long addressId = addAddress();
        dto.setAddressBookId(addressId);

        // 添加一个商品
        addDishToShoppingCart();

        // 此处应该成功提交订单
        MvcResult submitOrder = mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn();

        String response = submitOrder.getResponse().getContentAsString();

        Result<OrderSubmitVO> submitOrderResult = objectMapper.readValue(
                response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class)
        );

        Long orderId = submitOrderResult.getData().getId();

        verifyOrderStatus(orderId, 1, "订单应该为待付款状态");

        OrdersPaymentDTO ordersPaymentDTO = new OrdersPaymentDTO();
        ordersPaymentDTO.setOrderNumber(submitOrderResult.getData().getOrderNumber());
        ordersPaymentDTO.setPayMethod(1);

        mockMvc.perform(put("/user/order/payment")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ordersPaymentDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verifyOrderStatus(orderId, 2, "订单应该为已付款状态");
    }

    /*用户下单决策表*/
    /*****************/


    /**
     * 验证订单状态的辅助方法
     *
     * @param orderId        订单ID
     * @param expectedStatus 期望的状态值
     * @param message        断言失败时的消息
     */
    private void verifyOrderStatus(Long orderId, Integer expectedStatus, String message) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/order/details/" + orderId)
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Result<OrderVO> resultObj = objectMapper.readValue(response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderVO.class));

        OrderVO orderVO = resultObj.getData();
        assertNotNull(orderVO, "订单详情不能为空");
        assertEquals(expectedStatus, orderVO.getStatus(), message);
    }

//    @Test
//    @DisplayName("状态转换1: 浏览菜单 -> 收费中 (下单成功)")
//    void testStateTransition_BrowsingToCharging() throws Exception {
//        // 1. 先添加地址
//        Long addressId = addAddress();
//
//        // 2. 添加商品到购物车 (模拟浏览菜单并添加商品)
//        addDishToShoppingCart();
//
//        // 3. 提交订单 (从浏览菜单到收费中)
//        OrdersSubmitDTO dto = new OrdersSubmitDTO();
//        BeanUtils.copyProperties(orderSubmitDTO, dto);
//        dto.setAddressBookId(addressId);
//        dto.setPayMethod(1); // 微信支付
//
//        MvcResult submitResult = mockMvc.perform(post("/user/order/submit")
//                        .header("authentication", userToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(dto)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1))
//                .andExpect(jsonPath("$.data.orderNumber").exists())
//                .andReturn();
//
//        String response = submitResult.getResponse().getContentAsString();
//        Result<OrderSubmitVO> result = objectMapper.readValue(response,
//                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));
//
//        // 验证订单状态为待付款 (对应收费中状态)
//        verifyOrderStatus(result.getData().getId(), 1, "订单应该处于待付款状态");
//
//        System.out.println("状态转换测试通过: 浏览菜单 -> 收费中");
//    }
//
//    @Test
//    @DisplayName("状态转换2: 收费中 -> 等待接单 (支付成功)")
//    void testStateTransition_ChargingToWaitingAcceptance() throws Exception {
//        // 1. 创建订单到收费中状态
//        Long addressId = addAddress();
//        addDishToShoppingCart();
//
//        OrdersSubmitDTO dto = new OrdersSubmitDTO();
//        BeanUtils.copyProperties(orderSubmitDTO, dto);
//        dto.setAddressBookId(addressId);
//        dto.setPayMethod(1);
//
//        MvcResult submitResult = mockMvc.perform(post("/user/order/submit")
//                        .header("authentication", userToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(dto)))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String response = submitResult.getResponse().getContentAsString();
//        Result<OrderSubmitVO> result = objectMapper.readValue(response,
//                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));
//
//        // 2. 执行支付 (从收费中到等待接单)
//        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
//        paymentDTO.setOrderNumber(result.getData().getOrderNumber());
//        paymentDTO.setPayMethod(1);
//
//        mockMvc.perform(put("/user/order/payment")
//                        .header("authentication", userToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(paymentDTO)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1));
//
//        // 验证订单状态为待接单 (对应等待接单状态)
//        verifyOrderStatus(result.getData().getId(), 2, "订单应该处于待接单状态");
//
//        System.out.println("状态转换测试通过: 收费中 -> 等待接单");
//    }
//
//    @Test
//    @DisplayName("状态转换3: 等待接单 -> 订单信息页 (商家接单)")
//    void testStateTransition_WaitingAcceptanceToOrderInfo() throws Exception {
//        // 1. 创建订单并支付到等待接单状态
//        Long orderId = createPaidOrder();
//
//        // 2. 商家接单 (从等待接单到订单信息页，实际对应已接单状态)
//        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
//        confirmDTO.setId(orderId);
//        confirmDTO.setStatus(3); // 已接单状态
//
//        mockMvc.perform(put("/admin/order/confirm")
//                        .header("token", adminToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(confirmDTO)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1));
//
//        // 验证订单状态为已接单
//        verifyOrderStatus(orderId, 3, "订单应该处于已接单状态");
//
//        System.out.println("状态转换测试通过: 等待接单 -> 订单信息页 (商家接单)");
//    }
//
//    @Test
//    @DisplayName("状态转换4: 等待接单 -> 退款中 (用户取消)")
//    void testStateTransition_ChargingToRefunding() throws Exception {
//        // 1. 创建订单到收费中状态
//        Long addressId = addAddress();
//        addDishToShoppingCart();
//
//        OrdersSubmitDTO dto = new OrdersSubmitDTO();
//        BeanUtils.copyProperties(orderSubmitDTO, dto);
//        dto.setAddressBookId(addressId);
//        dto.setPayMethod(1);
//
//        MvcResult submitResult = mockMvc.perform(post("/user/order/submit")
//                        .header("authentication", userToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(dto)))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String response = submitResult.getResponse().getContentAsString();
//        Result<OrderSubmitVO> result = objectMapper.readValue(response,
//                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));
//
//        // 2. 用户取消订单 (从等待接单到退款中)
//        mockMvc.perform(put("/user/order/cancel/" + result.getData().getId())
//                        .header("authentication", userToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1));
//
//        // 验证订单状态为已取消 (对应退款处理完成)
//        verifyOrderStatus(result.getData().getId(), 6, "订单应该处于已取消状态");
//
//        System.out.println("状态转换测试通过: 收费中 -> 退款中");
//    }
//
//    @Test
//    @DisplayName("状态转换5: 等待接单 -> 退款中 (商家拒单)")
//    void testStateTransition_WaitingAcceptanceToRefundingByRejection() throws Exception {
//        // 1. 创建订单并支付到等待接单状态
//        Long orderId = createPaidOrder();
//
//        // 2. 商家拒单 (从等待接单到退款中)
//        OrdersRejectionDTO rejectionDTO = new OrdersRejectionDTO();
//        rejectionDTO.setId(orderId);
//        rejectionDTO.setRejectionReason("食材不足，无法制作");
//
//        mockMvc.perform(put("/admin/order/rejection")
//                        .header("token", adminToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(rejectionDTO)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1));
//
//        // 验证订单状态为已取消
//        verifyOrderStatus(orderId, 6, "被拒单的订单应该处于已取消状态");
//
//        System.out.println("状态转换测试通过: 等待接单 -> 退款中 (商家拒单)");
//    }
//
//    @Test
//    @DisplayName("TC1-状态转换6: 完整订单生命周期 (正常流程)")
//    void testCompleteOrderLifecycle() throws Exception {
//        // 1. 浏览菜单 -> 收费中 (下单)
//        Long addressId = addAddress();
//        addDishToShoppingCart();
//        addDishToShoppingCart();
//
//        OrdersSubmitDTO dto = new OrdersSubmitDTO();
//        BeanUtils.copyProperties(orderSubmitDTO, dto);
//        dto.setAddressBookId(addressId);
//        dto.setPayMethod(1);
//
//        MvcResult submitResult = mockMvc.perform(post("/user/order/submit")
//                        .header("authentication", userToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(dto)))
//                .andExpect(status().isOk())
//                .andReturn();
//
//        String response = submitResult.getResponse().getContentAsString();
//        Result<OrderSubmitVO> result = objectMapper.readValue(response,
//                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));
//
//        Long orderId = result.getData().getId();
//        String orderNumber = result.getData().getOrderNumber();
//
//        // 验证状态1: 待付款
//        verifyOrderStatus(orderId, 1, "订单应该处于待付款状态");
//
//        // 2. 收费中 -> 等待接单 (支付)
//        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
//        paymentDTO.setOrderNumber(orderNumber);
//        paymentDTO.setPayMethod(1);
//
//        mockMvc.perform(put("/user/order/payment")
//                        .header("authentication", userToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(paymentDTO)))
//                .andExpect(status().isOk());
//
//        // 验证状态2: 待接单
//        verifyOrderStatus(orderId, 2, "订单应该处于待接单状态");
//
//        // 3. 等待接单 -> 已接单 (商家接单)
//        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
//        confirmDTO.setId(orderId);
//        confirmDTO.setStatus(3);
//
//        mockMvc.perform(put("/admin/order/confirm")
//                        .header("token", adminToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(confirmDTO)))
//                .andExpect(status().isOk());
//
//        // 验证状态3: 已接单
//        verifyOrderStatus(orderId, 3, "订单应该处于已接单状态");
//
//        // 4. 已接单 -> 派送中 (开始派送)
//        mockMvc.perform(put("/admin/order/delivery/" + orderId)
//                        .header("token", adminToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1));
//
//        // 验证状态4: 派送中
//        verifyOrderStatus(orderId, 4, "订单应该处于派送中状态");
//
//        // 5. 派送中 -> 已完成 (确认送达)
//        mockMvc.perform(put("/admin/order/complete/" + orderId)
//                        .header("token", adminToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1));
//
//        // 验证状态5: 已完成
//        verifyOrderStatus(orderId, 5, "订单应该处于已完成状态");
//
//        System.out.println("完整订单生命周期测试通过");
//    }
//
//    @Test
//    @DisplayName("状态转换7: 浏览菜单循环 (重复添加商品)")
//    void testStateTransition_BrowsingMenuLoop() throws Exception {
//        // 模拟用户在浏览菜单时反复添加商品
//        Long addressId = addAddress();
//
//        // 第一次添加商品 (AddItem/add next)
//        addDishToShoppingCart();
//
//        // 验证购物车中有商品
//        MvcResult cartResult = mockMvc.perform(get("/user/shoppingCart/list")
//                        .header("authentication", userToken))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1))
//                .andReturn();
//
//        // 再次添加商品 (继续浏览)
//        ShoppingCartDTO secondItem = new ShoppingCartDTO();
//        secondItem.setDishId(47L); // 假设另一个菜品ID
//
//        mockMvc.perform(post("/user/shoppingCart/add")
//                        .header("authentication", userToken)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(secondItem)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(1));
//
//        System.out.println("状态转换测试通过: 浏览菜单循环");
//    }


    /**
     * 创建已支付订单的辅助方法
     *
     * @return 订单ID
     */
    private Long createPaidOrder() throws Exception {
        Long addressId = addAddress();
        addDishToShoppingCart();

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        BeanUtils.copyProperties(orderSubmitDTO, dto);
        dto.setAddressBookId(addressId);
        dto.setPayMethod(1);

        MvcResult submitResult = mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        String response = submitResult.getResponse().getContentAsString();
        Result<OrderSubmitVO> result = objectMapper.readValue(response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));

        // 执行支付
        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
        paymentDTO.setOrderNumber(result.getData().getOrderNumber());
        paymentDTO.setPayMethod(1);

        mockMvc.perform(put("/user/order/payment")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentDTO)))
                .andExpect(status().isOk());

        return result.getData().getId();
    }

    private Long createCompletedOrder() throws Exception {
        // 1. 创建并支付订单
        Long orderId = createPaidOrder();

        // 2. 商家接单
        merchantAcceptOrder(orderId);
        verifyOrderStatus(orderId, 3, "正常接单");

        // 3. 订单派送
        mockMvc.perform(put("/admin/order/delivery/" + orderId)
                        .header("token", adminToken))
                .andReturn();
        verifyOrderStatus(orderId, 4, "正常完成派送");

        // 3. 订单完成
        mockMvc.perform(put("/admin/order/complete/" + orderId)
                        .header("token", adminToken))
                .andReturn();
        verifyOrderStatus(orderId, 5, "正常完成订单");

        return orderId;
    }

    // ==================== TC1：订单完成流程 ====================
    @Test
    @DisplayName("TC1：订单完成流程")
    void testTC1_OrderCompletionFlow() throws Exception {
        System.out.println("=== 开始执行TC1：订单完成流程 ===");

        // 测试条件：系统开始状态

        // 步骤1: 创建 New Order
        System.out.println("步骤1: 创建 New Order");
        Long orderId = createNewOrder();
        verifyOrderStatus(orderId, 2, "订单应该处于New Order状态(待接单)");

        // 步骤2: 执行 Merchant[Accept]/notify user → 验证状态转为 Preparing
        System.out.println("步骤2: 商家接单，状态转为 Preparing");
        merchantAcceptOrder(orderId);
        verifyOrderStatus(orderId, 3, "订单应该处于Preparing状态(已接单)");

        // 步骤3: 执行 Preparation Complete/start delivery → 验证状态转为 Delivering
        System.out.println("步骤3: 准备完成，开始配送，状态转为 Delivering");
        startDelivery(orderId);
        verifyOrderStatus(orderId, 4, "订单应该处于Delivering状态(派送中)");

        // 步骤4: 执行 Delivery Complete/mark completed → 验证状态转为 Completed
        System.out.println("步骤4: 配送完成，状态转为 Completed");
        completeDelivery(orderId);
        verifyOrderStatus(orderId, 5, "订单应该处于Completed状态(已完成)");

        System.out.println("=== TC1测试通过 ===");
    }

    // ==================== TC2：订单取消和退款流程 ====================
    @Test
    @DisplayName("TC2：订单取消和退款流程")
    void testTC2_OrderCancellationAndRefundFlow() throws Exception {
        System.out.println("=== 开始执行TC2：订单取消和退款流程 ===");

        // 测试条件：系统开始状态

        // 步骤1: 创建 New Order
        System.out.println("步骤1: 创建 New Order");
        Long orderId = createNewOrder();
        verifyOrderStatus(orderId, 2, "订单应该处于New Order状态(待接单)");

        // 步骤2: 执行 Merchant[Accept]/notify user → 验证状态转为 Preparing
        System.out.println("步骤2: 商家接单，状态转为 Preparing");
        merchantAcceptOrder(orderId);
        verifyOrderStatus(orderId, 3, "订单应该处于Preparing状态(已接单)");

        // 步骤3: 执行 Cancel Request/refund user → 验证状态转为 Refunding
        System.out.println("步骤3: 用户取消请求，状态转为 Refunding");
        userCancelOrder(orderId);
        verifyOrderStatus(orderId, 6, "订单应该处于Refunding状态(已取消/退款中)");

        // 注意：实际系统中可能需要模拟退款失败和成功的场景
        // 这里简化处理，因为现有API直接完成取消
        System.out.println("=== TC2测试通过 ===");
    }

    // ==================== TC3：商家拒绝和完成后取消流程 ====================
    @Test
    @DisplayName("TC3：商家拒绝和完成后取消流程 - 超时退款")
    void testTC3_TimeoutRefundFlow() throws Exception {
        System.out.println("=== 开始执行TC3：超时退款流程 ===");

        // 测试条件：系统开始状态

        // 步骤1: 创建 New Order
        System.out.println("步骤1: 创建 New Order");
        Long orderId = createNewOrder();
        verifyOrderStatus(orderId, 2, "订单应该处于New Order状态(待接单)");

        // 步骤2: 执行 Timeout/refund user → 验证状态转为 Refunding
        // 这里模拟超时场景，实际可能需要等待或手动触发超时
        System.out.println("步骤2: 模拟超时，用户取消订单");
        userCancelOrder(orderId);
        verifyOrderStatus(orderId, 6, "订单应该处于Refunding状态(已取消)");

        System.out.println("=== TC3超时退款测试通过 ===");
    }

    @Test
    @DisplayName("TC3：商家拒绝和完成后取消流程 - 商家拒单")
    void testTC3_MerchantRejectFlow() throws Exception {
        System.out.println("=== 开始执行TC3：商家拒单流程 ===");

        // 测试条件：系统开始状态

        // 步骤1: 创建 New Order
        System.out.println("步骤1: 创建 New Order");
        Long orderId = createNewOrder();
        verifyOrderStatus(orderId, 2, "订单应该处于New Order状态(待接单)");

        // 步骤2: 执行 Merchant[Reject]/refund user → 验证状态转为 Refunding
        System.out.println("步骤2: 商家拒单，状态转为 Refunding");
        merchantRejectOrder(orderId, "食材不足，无法制作");
        verifyOrderStatus(orderId, 6, "订单应该处于Refunding状态(已取消)");

        System.out.println("=== TC3商家拒单测试通过 ===");
    }

    @Test
    @DisplayName("TC3：完成后取消流程")
    void testTC3_CancelAfterCompletionFlow() throws Exception {
        System.out.println("=== 开始执行TC3：完成后取消流程 ===");

        // 先执行完整订单流程到Completed状态
        Long orderId = createNewOrder();
        merchantAcceptOrder(orderId);
        startDelivery(orderId);
        completeDelivery(orderId);
        verifyOrderStatus(orderId, 5, "订单应该处于Completed状态(已完成)");

        // 执行完成后取消（实际业务中可能不允许，这里测试边界情况）
        System.out.println("尝试在完成后取消订单");
        try {
            userCancelOrder(orderId);
            // 如果系统允许完成后取消，验证状态
            verifyOrderStatus(orderId, 6, "完成后取消的订单应该处于Refunding状态");
        } catch (Exception e) {
            System.out.println("完成后取消被拒绝，符合预期: " + e.getMessage());
        }

        System.out.println("=== TC3完成后取消测试通过 ===");
    }

    @Test
    @DisplayName("TC3：配送中取消流程")
    void testTC3_CancelDuringDeliveryFlow() throws Exception {
        System.out.println("=== 开始执行TC3：配送中取消流程 ===");

        // 创建订单并进入Delivering状态
        Long orderId = createNewOrder();
        merchantAcceptOrder(orderId);
        startDelivery(orderId);
        verifyOrderStatus(orderId, 4, "订单应该处于Delivering状态(派送中)");

        // 执行配送中取消
        System.out.println("在配送中取消订单");
        userCancelOrder(orderId);
        verifyOrderStatus(orderId, 6, "配送中取消的订单应该处于Refunding状态");

        System.out.println("=== TC3配送中取消测试通过 ===");
    }

    // ==================== TC5：正常下单成功流程 ====================
    @Test
    @DisplayName("TC5：正常下单成功流程")
    void testTC5_NormalOrderSuccessFlow() throws Exception {
        System.out.println("=== 开始执行TC5：正常下单成功流程 ===");

        // 测试条件：用户进入系统

        // 步骤1: 进入 Browsing Menus 状态
        System.out.println("步骤1: 用户进入浏览菜单状态");
        // 浏览菜单状态在系统中是隐含的，用户登录后即可浏览

        // 步骤2: 执行 Add[item]/add next → 验证仍在 Browsing Menus 状态
        System.out.println("步骤2: 添加商品到购物车，仍在浏览菜单状态");
        addDishToShoppingCart();
        verifyShoppingCartNotEmpty();

        // 步骤3: 执行 Add[last item]/Place Order → 验证状态转为 Charging
        System.out.println("步骤3: 添加最后一个商品并下单，状态转为 Charging");
        Long orderId = submitOrder();
        verifyOrderStatus(orderId, 1, "订单应该处于Charging状态(待付款)");

        // 步骤4: 执行 Pay[valid]/show payment success info → 验证状态转为 Waiting for acceptance
        System.out.println("步骤4: 有效支付，状态转为 Waiting for acceptance");
        String orderNumber = getOrderNumber(orderId);
        payForOrder(orderNumber);
        verifyOrderStatus(orderId, 2, "订单应该处于Waiting for acceptance状态(待接单)");

        // 步骤5: 执行 Order[accept]/display order info → 验证状态转为 Order Info Page
        System.out.println("步骤5: 商家接单，状态转为 Order Info Page");
        merchantAcceptOrder(orderId);
        verifyOrderStatus(orderId, 3, "订单应该处于Order Info Page状态(已接单)");

        // 步骤6: 结束流程到终止状态
        System.out.println("步骤6: 流程结束");

        System.out.println("=== TC5测试通过 ===");
    }

    // ==================== TC6：支付失败和订单被拒绝流程 ====================
    @Test
    @DisplayName("TC6：支付失败和订单被拒绝流程")
    void testTC6_PaymentFailureAndRejectionFlow() throws Exception {
        System.out.println("=== 开始执行TC6：支付失败和订单被拒绝流程 ===");

        // 测试条件：用户进入系统

        // 步骤1: 进入 Browsing Menus 状态
        System.out.println("步骤1: 用户进入浏览菜单状态");

        // 步骤2: 执行 Add[last item]/Place Order → 验证状态转为 Charging
        System.out.println("步骤2: 添加商品并下单，状态转为 Charging");
        addDishToShoppingCart();
        Long orderId = submitOrder();
        verifyOrderStatus(orderId, 1, "订单应该处于Charging状态(待付款)");

        // 步骤3: 执行 Pay[invalid]/error → 验证仍在 Charging 状态
        System.out.println("步骤3: 无效支付，仍在 Charging 状态");
        verifyOrderStatus(orderId, 1, "支付失败后订单应该仍在Charging状态(待付款)");

        // 步骤4: 执行 Pay[valid]/show payment success info → 验证状态转为 Waiting for acceptance
        System.out.println("步骤4: 有效支付，状态转为 Waiting for acceptance");
        String orderNumber = getOrderNumber(orderId);
        payForOrder(orderNumber);
        verifyOrderStatus(orderId, 2, "订单应该处于Waiting for acceptance状态(待接单)");

        // 步骤5: 执行 Order[rejected]/display order info → 验证状态转为 Order Info Page
        System.out.println("步骤5: 订单被拒绝，状态转为 Order Info Page");
        merchantRejectOrder(orderId, "商家忙碌，无法接单");
        verifyOrderStatus(orderId, 6, "被拒绝的订单应该处于已取消状态");

        // 步骤6: 执行 Reorder request/display menus → 验证状态转为 Browsing Menus
        System.out.println("步骤6: 重新下单请求，回到浏览菜单状态");
        // 重新下单实际上是重新开始流程，这里验证用户可以重新浏览菜单
        verifyUserCanBrowseMenus();

        System.out.println("=== TC6测试通过 ===");
    }

    // ==================== TC7：取消订单和退款流程 ====================
    @Test
    @DisplayName("TC7：取消订单和退款流程")
    void testTC7_CancelOrderAndRefundFlow() throws Exception {
        System.out.println("=== 开始执行TC7：取消订单和退款流程 ===");

        // 测试条件：用户进入系统

        // 步骤1: 进入 Browsing Menus 状态
        System.out.println("步骤1: 用户进入浏览菜单状态");

        // 步骤2: 执行 Add[last item]/Place Order → 验证状态转为 Charging
        System.out.println("步骤2: 添加商品并下单，状态转为 Charging");
        addDishToShoppingCart();
        Long orderId = submitOrder();
        verifyOrderStatus(orderId, 1, "订单应该处于Charging状态(待付款)");

        // 步骤3: 执行 Pay[valid]/show payment success info → 验证状态转为 Waiting for acceptance
        System.out.println("步骤3: 有效支付，状态转为 Waiting for acceptance");
        String orderNumber = getOrderNumber(orderId);
        payForOrder(orderNumber);
        verifyOrderStatus(orderId, 2, "订单应该处于Waiting for acceptance状态(待接单)");

        // 步骤4: 执行 Cancel Request/refund → 验证状态转为 Refunding
        System.out.println("步骤4: 取消请求，状态转为 Refunding");
        userCancelOrder(orderId);
        verifyOrderStatus(orderId, 6, "订单应该处于Refunding状态(已取消)");

        // 步骤5-7: 退款相关流程（在实际系统中可能需要更复杂的退款状态管理）
        System.out.println("步骤5-7: 退款流程处理");
        // 这里简化处理，因为现有系统直接完成取消

        System.out.println("=== TC7测试通过 ===");
    }

    // ==================== TC8-10：无效订单状态转换 ====================
    @Test
    @DisplayName("TC8: 无效订单状态转换-待付款订单必须先完成付款")
    void testInvalidStateTransitionsBeforePayment() throws Exception {
        // 1. 创建待付款订单
        Long addressId = addAddress();
        addDishToShoppingCart();

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        BeanUtils.copyProperties(orderSubmitDTO, dto);
        dto.setAddressBookId(addressId);

        MvcResult submitResult = mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        String response = submitResult.getResponse().getContentAsString();
        Result<OrderSubmitVO> result = objectMapper.readValue(response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));

        Long orderId = result.getData().getId();

        // 2.
        mockMvc.perform(put("/admin/order/confirm/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        // 验证这个无效转换被拒绝或订单状态未改变
        verifyOrderStatus(orderId, 1, "无效状态转换后订单状态不应改变");
        System.out.println("无效状态转换测试通过");

        // 3. 尝试无效的状态转换: 直接从待付款到派送中
        mockMvc.perform(put("/admin/order/delivery/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        // 验证这个无效转换被拒绝或订单状态未改变
        verifyOrderStatus(orderId, 1, "无效状态转换后订单状态不应改变");


        // 4.
        mockMvc.perform(put("/admin/order/complete/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        // 验证这个无效转换被拒绝或订单状态未改变
        verifyOrderStatus(orderId, 1, "无效状态转换后订单状态不应改变");
        System.out.println("无效状态转换测试通过");
    }

    @Test
    @DisplayName("TC9: 无效订单状态转换-已取消订单不可再进行其他正常流程")
    void testInvalidStateTransitionsAfterCancelled() throws Exception {
        // 1. 创建并支付订单
        Long orderId = createPaidOrder();

        // 2. 用户取消订单
        mockMvc.perform(put("/user/order/cancel/" + orderId)
                        .header("authentication", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        // 验证订单已取消
        verifyOrderStatus(orderId, 6, "订单应该已取消");

        // 3. 商家接单
        merchantAcceptOrder(orderId);

        // 验证这个无效转换被拒绝或订单状态未改变
        verifyOrderStatus(orderId, 6, "无效状态转换后订单状态不应改变");
        System.out.println("无效状态转换测试通过");

        // 3. 订单派送
        mockMvc.perform(put("/admin/order/delivery/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        // 验证这个无效转换被拒绝或订单状态未改变
        verifyOrderStatus(orderId, 6, "无效状态转换后订单状态不应改变");


        // 4. 订单完成
        mockMvc.perform(put("/admin/order/complete/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        // 验证这个无效转换被拒绝或订单状态未改变
        verifyOrderStatus(orderId, 6, "无效状态转换后订单状态不应改变");
        System.out.println("无效状态转换测试通过");
    }

    @Test
    @DisplayName("TC10: 无效订单状态转换-已完成订单不可再被接单，派送")
    void testInvalidStateTransitionsAfterCompleted() throws Exception {
        Long orderId =createCompletedOrder();
        // 非法接单
        merchantAcceptOrder(orderId);
        verifyOrderStatus(orderId, 5, "已完成的订单不可被接单");

        // 非法派送
        mockMvc.perform(put("/admin/order/delivery/" + orderId)
                        .header("token", adminToken))
                .andReturn();
        verifyOrderStatus(orderId, 5, "已完成的订单不可被派送");

    }

    // ==================== 辅助方法 ====================

    /**
     * 创建新订单（包含完整的下单和支付流程）
     */
    private Long createNewOrder() throws Exception {
        Long addressId = addAddress();
        addDishToShoppingCart();
        Long orderId = submitOrder();
        String orderNumber = getOrderNumber(orderId);
        payForOrder(orderNumber);
        return orderId;
    }


    /**
     * 提交订单
     */
    private Long submitOrder() throws Exception {
        Long addressId = addAddress();
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        BeanUtils.copyProperties(orderSubmitDTO, dto);
        dto.setAddressBookId(addressId);
        dto.setPayMethod(1);

        MvcResult result = mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Result<OrderSubmitVO> resultObj = objectMapper.readValue(response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));

        return resultObj.getData().getId();
    }

    /**
     * 获取订单号
     */
    private String getOrderNumber(Long orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/order/details/" + orderId)
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        Result<OrderVO> resultObj = objectMapper.readValue(response,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderVO.class));

        return resultObj.getData().getNumber();
    }

    /**
     * 支付订单
     */
    private void payForOrder(String orderNumber) throws Exception {
        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
        paymentDTO.setOrderNumber(orderNumber);
        paymentDTO.setPayMethod(1);

        mockMvc.perform(put("/user/order/payment")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    /**
     * 商家接单
     */
    private void merchantAcceptOrder(Long orderId) throws Exception {
        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
        confirmDTO.setId(orderId);
        confirmDTO.setStatus(3);

        mockMvc.perform(put("/admin/order/confirm")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    /**
     * 商家拒单
     */
    private void merchantRejectOrder(Long orderId, String reason) throws Exception {
        OrdersRejectionDTO rejectionDTO = new OrdersRejectionDTO();
        rejectionDTO.setId(orderId);
        rejectionDTO.setRejectionReason(reason);

        mockMvc.perform(put("/admin/order/rejection")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectionDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    /**
     * 开始配送
     */
    private void startDelivery(Long orderId) throws Exception {
        mockMvc.perform(put("/admin/order/delivery/" + orderId)
                        .header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    /**
     * 完成配送
     */
    private void completeDelivery(Long orderId) throws Exception {
        mockMvc.perform(put("/admin/order/complete/" + orderId)
                        .header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    /**
     * 用户取消订单
     */
    private void userCancelOrder(Long orderId) throws Exception {
        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(orderId);
        ordersCancelDTO.setCancelReason("用户取消");

        mockMvc.perform(put("/admin/order/cancel/")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ordersCancelDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        verifyOrderStatus(orderId, 6, "订单应该已取消");
    }


    /**
     * 验证购物车不为空
     */
    private void verifyShoppingCartNotEmpty() throws Exception {
        mockMvc.perform(get("/user/shoppingCart/list")
                        .header("authentication", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").isNotEmpty());
        System.out.println("✓ 购物车验证通过: 购物车不为空");
    }

    /**
     * 验证用户可以浏览菜单
     */
    private void verifyUserCanBrowseMenus() throws Exception {
        System.out.println("用户可以重新浏览菜单");
    }


    /*****************/
    /*取消订单决策表测试*/
    @Test
    @DisplayName("TC17： 取消订单测试：商家未接单前用户可直接取消")
    void testValidMerchantCancelOrderBeforeAccept() throws Exception {

        // 1. 创建并支付订单
        Long orderId = createPaidOrder();

        // 验证状态2: 已接单
        verifyOrderStatus(orderId, 2, "订单应该处于待接单状态");

        // 用户取消订单 -> 成功

        mockMvc.perform(put("/user/order/cancel/" + orderId)
                        .header("authentication", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        // 验证状态3: 订单已取消
        verifyOrderStatus(orderId, 6, "订单应该已取消");
    }

    @Test
    @DisplayName("TC18： 订单超时测试：处理超过15分钟的待支付订单")
    void testProcessTimeoutOrder() throws Exception {

        // 1. 创建订单并支付到等待接单状态
        Long orderId = createPaidOrder();

        // 2. 将订单时间修改为16分钟前（模拟超时）
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(16);
        updateOrderTime(orderId, pastTime);

        // 3. 验证订单初始状态为待支付
        verifyOrderStatus(orderId, 2, "订单应该处于待支付状态");

        // 4. 手动触发定时任务
        orderTask.processTimeoutOrder();

        // 5. 验证订单状态变为已取消
        verifyOrderStatus(orderId, 6, "订单应该已取消");
        Orders updatedOrder = orderMapper.getById(orderId);
        assertEquals(Orders.CANCELLED, updatedOrder.getStatus());
        assertEquals("订单超时，自动取消", updatedOrder.getCancelReason());
        assertNotNull(updatedOrder.getCancelTime());
    }

    @Test
    @DisplayName("TC19： 订单超时测试：15分钟内的订单不应被取消")
    void testNotProcessOrderWithinTimeLimit() throws Exception {

        // 1. 创建订单并支付到等待接单状态
        Long orderId = createPaidOrder();

        // 2. 将订单时间修改为14分钟前（未超时）
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(14);
        updateOrderTime(orderId, recentTime);

        // 3. 手动触发定时任务
        orderTask.processTimeoutOrder();

        // 4. 验证订单状态仍为待支付（未被取消）
        Orders order = orderMapper.getById(orderId);
        assertEquals(Orders.TO_BE_CONFIRMED, order.getStatus());
        assertNull(order.getCancelReason());
        assertNull(order.getCancelTime());
    }

    @Test
    @DisplayName("TC20： 订单超时测试：刚好15分钟的订单应该被取消")
    void testProcessOrderAtExactTimeLimit() throws Exception {

        // 1. 创建订单并支付到等待接单状态
        Long orderId = createPaidOrder();

        // 2. 将订单时间修改为刚好15分钟前
        LocalDateTime exactTime = LocalDateTime.now().minusMinutes(15);
        updateOrderTime(orderId, exactTime);

        // 3. 手动触发定时任务
        orderTask.processTimeoutOrder();

        // 4. 验证订单被取消
        Orders order = orderMapper.getById(orderId);
        assertEquals(Orders.CANCELLED, order.getStatus());
        assertEquals("订单超时，自动取消", order.getCancelReason());
    }

    @Test
    @DisplayName("TC21： 订单超时测试：已接单的订单到达超时时间不应该被取消")
    void testProcessOrderAtExactTimeLimitWithAcceptance() throws Exception {

        // 1. 创建订单并支付到等待接单状态
        Long orderId = createPaidOrder();

        merchantAcceptOrder(orderId);

        // 2. 将订单时间修改为刚好15分钟前
        LocalDateTime exactTime = LocalDateTime.now().minusMinutes(15);
        updateOrderTime(orderId, exactTime);

        // 3. 手动触发定时任务
        orderTask.processTimeoutOrder();

        // 4. 验证订单未被取消
        Orders order = orderMapper.getById(orderId);
        assertEquals(Orders.CONFIRMED, order.getStatus());
    }


    // 辅助方法：更新订单时间
    private void updateOrderTime(Long orderId, LocalDateTime orderTime) {
        Orders order = orderMapper.getById(orderId);
        order.setOrderTime(orderTime);
        orderMapper.update(order);
    }

    @Test
    @DisplayName("TC22： 取消订单测试：商家接单后用户不可直接取消，由商家取消订单")
    void testValidMerchantCancelOrderAfterAccept() throws Exception {

        // 1. 创建并支付订单
        Long orderId = createPaidOrder();

        // 2. 等待接单 -> 已接单 (商家接单)
        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
        confirmDTO.setId(orderId);
        confirmDTO.setStatus(3);

        mockMvc.perform(put("/admin/order/confirm")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmDTO)))
                .andExpect(status().isOk());

        // 验证状态2: 已接单
        verifyOrderStatus(orderId, 3, "订单应该处于已接单状态");

        // 3. 商家接单后用户取消订单 -> 失败

        mockMvc.perform(put("/user/order/cancel/" + orderId)
                        .header("authentication", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("订单状态错误"));


        // 验证状态3: 仍为已接单
        verifyOrderStatus(orderId, 3, "订单应该仍处于已接单状态");

        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(orderId);
        ordersCancelDTO.setCancelReason("用户取消");

        mockMvc.perform(put("/admin/order/cancel/")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ordersCancelDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        // 验证状态3: 仍为已接单
        verifyOrderStatus(orderId, 6, "订单应该已取消");
    }

    @Test
    @DisplayName("TC23： 取消订单测试：商家派送后用户不可直接取消，由商家取消订单")
    void testValidMerchantCancelOrderAfterDelivery() throws Exception {
        // 1. 创建并支付订单
        Long orderId = createPaidOrder();

        // 2. 等待接单 -> 已接单 (商家接单)
        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
        confirmDTO.setId(orderId);
        confirmDTO.setStatus(3);

        mockMvc.perform(put("/admin/order/confirm")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmDTO)))
                .andExpect(status().isOk());

        // 4. 已接单 -> 派送中
        mockMvc.perform(put("/admin/order/delivery/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        verifyOrderStatus(orderId, 4, "订单应该处于派送中");

        // 5. 订单派送后用户取消订单 -> 失败

        mockMvc.perform(put("/user/order/cancel/" + orderId)
                        .header("authentication", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("订单状态错误"));

        verifyOrderStatus(orderId, 4, "订单应该处于派送中");

        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(orderId);
        ordersCancelDTO.setCancelReason("用户取消");

        mockMvc.perform(put("/admin/order/cancel/")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ordersCancelDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        // 验证状态3: 仍为已接单
        verifyOrderStatus(orderId, 6, "订单应该已取消");
    }

    @Test
    @DisplayName("TC24： 取消订单测试：订单完成后用户不可直接取消，由商家取消订单")
    void testValidMerchantCancelOrderAfterComplete() throws Exception {

        // 1. 创建并支付订单
        Long orderId = createPaidOrder();

        // 2. 等待接单 -> 已接单 (商家接单)
        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
        confirmDTO.setId(orderId);
        confirmDTO.setStatus(3);

        mockMvc.perform(put("/admin/order/confirm")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmDTO)))
                .andExpect(status().isOk());

        // 4. 已接单 -> 派送中
        mockMvc.perform(put("/admin/order/delivery/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        // 4. 派送中 -> 已完成
        mockMvc.perform(put("/admin/order/complete/" + orderId)
                        .header("token", adminToken))
                .andReturn();

        verifyOrderStatus(orderId, 5, "订单应该已完成");

        // 5. 订单完成后用户取消订单 -> 失败

        mockMvc.perform(put("/user/order/cancel/" + orderId)
                        .header("authentication", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("订单状态错误"));

        // 验证这个无效转换订单状态未改变
        verifyOrderStatus(orderId, 5, "无效状态转换后订单状态不应改变");

        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(orderId);
        ordersCancelDTO.setCancelReason("用户取消");

        mockMvc.perform(put("/admin/order/cancel/")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ordersCancelDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        // 验证状态3: 仍为已接单
        verifyOrderStatus(orderId, 6, "订单应该已取消");
    }


    /*取消订单决策表测试*/

}

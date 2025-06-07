package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
        import com.sky.entity.*;
        import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
        import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
        import static org.mockito.ArgumentMatchers.*;
        import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl 白盒测试类
 * 覆盖所有分支、路径和边界条件
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceImplWhiteBoxTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderDetailMapper orderDetailMapper;

    @Mock
    private AddressBookMapper addressBookMapper;

    @Mock
    private ShoppingCartMapper shoppingCartMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private WeChatPayUtil weChatPayUtil;

    @Mock
    private WebSocketServer webSocketServer;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Long testUserId = 1L;
    private Long testOrderId = 100L;
    private String testOrderNumber = "123456789";


    @BeforeEach
    void setUp() {
        // 模拟当前用户ID
        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)) {
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(testUserId);
        }
    }

    // ==================== submitOrder 方法测试 ====================

    @Test
    void testSubmitOrder_Success() {
        // 准备测试数据
        OrdersSubmitDTO ordersSubmitDTO = new OrdersSubmitDTO();
        ordersSubmitDTO.setAddressBookId(1L);
        ordersSubmitDTO.setAmount(new BigDecimal("50.00"));
        ordersSubmitDTO.setPackAmount(2);
        ordersSubmitDTO.setTablewareNumber(0);

        AddressBook addressBook = new AddressBook();
        addressBook.setId(1L);
        addressBook.setDetail("测试地址");
        addressBook.setPhone("13800138000");
        addressBook.setConsignee("张三");

        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        ShoppingCart cart = new ShoppingCart();
        cart.setId(1L);
        cart.setUserId(testUserId);
        cart.setDishId(1L);
        cart.setName("测试菜品");
        cart.setAmount(new BigDecimal("25.00"));
        cart.setNumber(2);
        shoppingCartList.add(cart);

        Orders savedOrder = new Orders();
        savedOrder.setId(testOrderId);

        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)) {
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(testUserId);

            // Mock 依赖方法
            when(addressBookMapper.getById(1L)).thenReturn(addressBook);
            when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(shoppingCartList);
            doAnswer(invocation -> {
                Orders order = invocation.getArgument(0);
                order.setId(testOrderId); // 模拟设置ID
                return null;
            }).when(orderMapper).insert(any(Orders.class));

            // 执行测试
            OrderSubmitVO result = orderService.submitOrder(ordersSubmitDTO);

            // 验证结果
            assertNotNull(result);
            assertEquals(testOrderId, result.getId());
            assertNotNull(result.getOrderTime());
            assertNotNull(result.getOrderNumber());

            // 验证方法调用
            verify(addressBookMapper).getById(1L);
            verify(shoppingCartMapper).list(any(ShoppingCart.class));
            verify(orderMapper).insert(any(Orders.class));
            verify(orderDetailMapper).insertBatch(anyList());
            verify(shoppingCartMapper).deleteByUserId(testUserId);
        }
    }

    @Test
    void testSubmitOrder_AddressBookNull() {
        // 准备测试数据
        OrdersSubmitDTO ordersSubmitDTO = new OrdersSubmitDTO();
        ordersSubmitDTO.setAddressBookId(1L);

        // Mock 地址簿为空
        when(addressBookMapper.getById(1L)).thenReturn(null);

        // 执行测试并验证异常
        AddressBookBusinessException exception = assertThrows(
                AddressBookBusinessException.class,
                () -> orderService.submitOrder(ordersSubmitDTO)
        );

        assertEquals(MessageConstant.ADDRESS_BOOK_IS_NULL, exception.getMessage());
        verify(addressBookMapper).getById(1L);
    }

    @Test
    void testSubmitOrder_ShoppingCartNull() {
        // 准备测试数据
        OrdersSubmitDTO ordersSubmitDTO = new OrdersSubmitDTO();
        ordersSubmitDTO.setAddressBookId(1L);

        AddressBook addressBook = new AddressBook();
        addressBook.setId(1L);

        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)) {
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(testUserId);

            // Mock 购物车为空
            when(addressBookMapper.getById(1L)).thenReturn(addressBook);
            when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(null);

            // 执行测试并验证异常
            ShoppingCartBusinessException exception = assertThrows(
                    ShoppingCartBusinessException.class,
                    () -> orderService.submitOrder(ordersSubmitDTO)
            );

            assertEquals(MessageConstant.SHOPPING_CART_IS_NULL, exception.getMessage());
        }
    }

    @Test
    void testSubmitOrder_ShoppingCartEmpty() {
        // 准备测试数据
        OrdersSubmitDTO ordersSubmitDTO = new OrdersSubmitDTO();
        ordersSubmitDTO.setAddressBookId(1L);

        AddressBook addressBook = new AddressBook();
        addressBook.setId(1L);

        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)) {
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(testUserId);

            // Mock 购物车为空列表
            when(addressBookMapper.getById(1L)).thenReturn(addressBook);
            when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(new ArrayList<>());

            // 执行测试并验证异常
            ShoppingCartBusinessException exception = assertThrows(
                    ShoppingCartBusinessException.class,
                    () -> orderService.submitOrder(ordersSubmitDTO)
            );

            assertEquals(MessageConstant.SHOPPING_CART_IS_NULL, exception.getMessage());
        }
    }

    // ==================== payment 方法测试 ====================

    @Test
    void testPayment_Success() throws Exception {
        // 准备测试数据
        OrdersPaymentDTO ordersPaymentDTO = new OrdersPaymentDTO();
        ordersPaymentDTO.setOrderNumber(testOrderNumber);

        User user = new User();
        user.setId(testUserId);
        user.setOpenid("test-openid");

        com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
        jsonObject.put("prepay_id", "test-prepay-id");
        jsonObject.put("package", "test-package");

        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)) {
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(testUserId);

            // Mock 依赖方法
            when(userMapper.getById(testUserId)).thenReturn(user);
            when(weChatPayUtil.pay(eq(testOrderNumber), any(BigDecimal.class), eq("同济外卖订单"), eq("test-openid")))
                    .thenReturn(jsonObject);

            // 执行测试
            OrderPaymentVO result = orderService.payment(ordersPaymentDTO);

            // 验证结果
            assertNotNull(result);
            assertEquals("test-package", result.getPackageStr());

            // 验证方法调用
            verify(userMapper).getById(testUserId);
            verify(weChatPayUtil).pay(eq(testOrderNumber), any(BigDecimal.class), eq("同济外卖订单"), eq("test-openid"));
        }
    }

    @Test
    void testPayment_OrderAlreadyPaid() throws Exception {
        // 准备测试数据
        OrdersPaymentDTO ordersPaymentDTO = new OrdersPaymentDTO();
        ordersPaymentDTO.setOrderNumber(testOrderNumber);

        User user = new User();
        user.setId(testUserId);
        user.setOpenid("test-openid");

        com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
        jsonObject.put("code", "ORDERPAID");

        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)) {
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(testUserId);

            // Mock 依赖方法
            when(userMapper.getById(testUserId)).thenReturn(user);
            when(weChatPayUtil.pay(eq(testOrderNumber), any(BigDecimal.class), eq("同济外卖订单"), eq("test-openid")))
                    .thenReturn(jsonObject);

            // 执行测试并验证异常
            OrderBusinessException exception = assertThrows(
                    OrderBusinessException.class,
                    () -> orderService.payment(ordersPaymentDTO)
            );

            assertEquals("该订单已支付", exception.getMessage());
        }
    }

    // ==================== paySuccess 方法测试 ====================

    @Test
    void testPaySuccess_Success() {
        // 准备测试数据
        Orders orderDB = new Orders();
        orderDB.setId(testOrderId);
        orderDB.setNumber(testOrderNumber);

        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class);
             MockedStatic<JSON> jsonMock = mockStatic(JSON.class)) {

            baseContextMock.when(BaseContext::getCurrentId).thenReturn(testUserId);
            jsonMock.when(() -> JSON.toJSONString(any())).thenReturn("{\"type\":1,\"orderId\":100,\"content\":\"订单号：123456789\"}");

            // Mock 依赖方法
            when(orderMapper.getByNumberAndUserId(testOrderNumber, testUserId)).thenReturn(orderDB);

            // 执行测试
            orderService.paySuccess(testOrderNumber);

            // 验证方法调用
            verify(orderMapper).getByNumberAndUserId(testOrderNumber, testUserId);
            verify(orderMapper).update(any(Orders.class));
            verify(webSocketServer).sendToAllClient(anyString());
        }
    }

    // ==================== details 方法测试 ====================

    @Test
    void testDetails_Success() {
        // 准备测试数据
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setNumber(testOrderNumber);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        OrderDetail detail = new OrderDetail();
        detail.setId(1L);
        detail.setOrderId(testOrderId);
        orderDetailList.add(detail);

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);
        when(orderDetailMapper.getByOrderId(testOrderId)).thenReturn(orderDetailList);

        // 执行测试
        OrderVO result = orderService.details(testOrderId);

        // 验证结果
        assertNotNull(result);
        assertEquals(testOrderId, result.getId());
        assertEquals(testOrderNumber, result.getNumber());
        assertEquals(1, result.getOrderDetailList().size());

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderDetailMapper).getByOrderId(testOrderId);
    }

    // ==================== userCancelById 方法测试 ====================

    @Test
    void testUserCancelById_OrderNotFound() {
        // Mock 订单不存在
        when(orderMapper.getById(testOrderId)).thenReturn(null);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.userCancelById(testOrderId)
        );

        assertEquals(MessageConstant.ORDER_NOT_FOUND, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testUserCancelById_StatusError() {
        // 准备测试数据 - 订单状态为已接单(3)
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.CONFIRMED); // 状态为3，大于2

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.userCancelById(testOrderId)
        );

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testUserCancelById_PendingPayment() throws Exception {
        // 准备测试数据 - 待付款状态
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.PENDING_PAYMENT); // 状态为1

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.userCancelById(testOrderId);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(any(Orders.class));

        // 验证更新的订单状态
        verify(orderMapper).update(argThat(orders ->
                orders.getStatus().equals(Orders.CANCELLED) &&
                        "用户取消".equals(orders.getCancelReason()) &&
                        orders.getCancelTime() != null
        ));
    }

    @Test
    void testUserCancelById_ToBeConfirmed() throws Exception {
        // 准备测试数据 - 待接单状态，需要退款
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.TO_BE_CONFIRMED); // 状态为2
        order.setNumber(testOrderNumber);

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.userCancelById(testOrderId);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(any(Orders.class));

        // 验证更新的订单状态包含退款状态
        verify(orderMapper).update(argThat(orders ->
                orders.getStatus().equals(Orders.CANCELLED) &&
                        orders.getPayStatus().equals(Orders.REFUND) &&
                        "用户取消".equals(orders.getCancelReason())
        ));
    }

    // ==================== confirm 方法测试 ====================

    @Test
    void testConfirm_Success() {
        // 准备测试数据
        OrdersConfirmDTO ordersConfirmDTO = new OrdersConfirmDTO();
        ordersConfirmDTO.setId(testOrderId);

        // 执行测试
        orderService.confirm(ordersConfirmDTO);

        // 验证方法调用
        verify(orderMapper).update(argThat(orders ->
                orders.getId().equals(testOrderId) &&
                        orders.getStatus().equals(Orders.CONFIRMED)
        ));
    }

    // ==================== rejection 方法测试 ====================

    @Test
    void testRejection_OrderNotFound() {
        // 准备测试数据
        OrdersRejectionDTO ordersRejectionDTO = new OrdersRejectionDTO();
        ordersRejectionDTO.setId(testOrderId);

        // Mock 订单不存在
        when(orderMapper.getById(testOrderId)).thenReturn(null);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.rejection(ordersRejectionDTO)
        );

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testRejection_StatusError() {
        // 准备测试数据
        OrdersRejectionDTO ordersRejectionDTO = new OrdersRejectionDTO();
        ordersRejectionDTO.setId(testOrderId);

        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.CONFIRMED); // 状态不是待接单

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.rejection(ordersRejectionDTO)
        );

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testRejection_NotPaid() throws Exception {
        // 准备测试数据
        OrdersRejectionDTO ordersRejectionDTO = new OrdersRejectionDTO();
        ordersRejectionDTO.setId(testOrderId);
        ordersRejectionDTO.setRejectionReason("商品缺货");

        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.TO_BE_CONFIRMED);
        order.setPayStatus(Orders.UN_PAID); // 未支付

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.rejection(ordersRejectionDTO);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(any(Orders.class));

        // 验证更新的订单状态
        verify(orderMapper).update(argThat(orders ->
                orders.getStatus().equals(Orders.CANCELLED) &&
                        "商品缺货".equals(orders.getRejectionReason())
        ));
    }

    @Test
    void testRejection_Paid() throws Exception {
        // 准备测试数据
        OrdersRejectionDTO ordersRejectionDTO = new OrdersRejectionDTO();
        ordersRejectionDTO.setId(testOrderId);
        ordersRejectionDTO.setRejectionReason("商品缺货");

        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.TO_BE_CONFIRMED);
        order.setPayStatus(Orders.PAID); // 已支付，需要退款
        order.setNumber(testOrderNumber);

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.rejection(ordersRejectionDTO);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(any(Orders.class));

        // 验证更新的订单状态
        verify(orderMapper).update(argThat(orders ->
                orders.getStatus().equals(Orders.CANCELLED) &&
                        "商品缺货".equals(orders.getRejectionReason())
        ));
    }

    // ==================== cancel 方法测试 ====================

    @Test
    void testCancel_NotPaid() throws Exception {
        // 准备测试数据
        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(testOrderId);
        ordersCancelDTO.setCancelReason("管理员取消");

        Orders order = new Orders();
        order.setId(testOrderId);
        order.setPayStatus(Orders.UN_PAID); // 未支付

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.cancel(ordersCancelDTO);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(any(Orders.class));

        // 验证更新的订单状态
        verify(orderMapper).update(argThat(orders ->
                orders.getStatus().equals(Orders.CANCELLED) &&
                        "管理员取消".equals(orders.getCancelReason())
        ));
    }

    @Test
    void testCancel_Paid() throws Exception {
        // 准备测试数据
        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(testOrderId);
        ordersCancelDTO.setCancelReason("管理员取消");

        Orders order = new Orders();
        order.setId(testOrderId);
        order.setPayStatus(Orders.PAID); // 已支付，需要退款
        order.setNumber(testOrderNumber);

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.cancel(ordersCancelDTO);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(any(Orders.class));

        // 验证更新的订单状态
        verify(orderMapper).update(argThat(orders ->
                orders.getStatus().equals(Orders.CANCELLED) &&
                        "管理员取消".equals(orders.getCancelReason())
        ));
    }

    // ==================== delivery 方法测试 ====================

    @Test
    void testDelivery_OrderNotFound() {
        // Mock 订单不存在
        when(orderMapper.getById(testOrderId)).thenReturn(null);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.delivery(testOrderId)
        );

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testDelivery_StatusError() {
        // 准备测试数据 - 订单状态不是已接单
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.TO_BE_CONFIRMED); // 状态不是已接单

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.delivery(testOrderId)
        );

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testDelivery_Success() {
        // 准备测试数据
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.CONFIRMED); // 已接单状态

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.delivery(testOrderId);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(argThat(orders ->
                orders.getId().equals(testOrderId) &&
                        orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)
        ));
    }

    // ==================== complete 方法测试 ====================

    @Test
    void testComplete_OrderNotFound() {
        // Mock 订单不存在
        when(orderMapper.getById(testOrderId)).thenReturn(null);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.complete(testOrderId)
        );

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testComplete_StatusError() {
        // 准备测试数据 - 订单状态不是派送中
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.CONFIRMED); // 状态不是派送中

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试并验证异常
        OrderBusinessException exception = assertThrows(
                OrderBusinessException.class,
                () -> orderService.complete(testOrderId)
        );

        assertEquals(MessageConstant.ORDER_STATUS_ERROR, exception.getMessage());
        verify(orderMapper).getById(testOrderId);
    }

    @Test
    void testComplete_Success() {
        // 准备测试数据
        Orders order = new Orders();
        order.setId(testOrderId);
        order.setStatus(Orders.DELIVERY_IN_PROGRESS); // 派送中状态

        // Mock 依赖方法
        when(orderMapper.getById(testOrderId)).thenReturn(order);

        // 执行测试
        orderService.complete(testOrderId);

        // 验证方法调用
        verify(orderMapper).getById(testOrderId);
        verify(orderMapper).update(argThat(orders ->
                orders.getId().equals(testOrderId) &&
                        orders.getStatus().equals(Orders.COMPLETED) &&
                        orders.getDeliveryTime() != null
        ));
    }

    // getOrderDishesStr 方法的测试
    @Test
    void testGetOrderDishesStr_Success() {
        // 准备测试数据
        Orders orders = new Orders();
        orders.setId(testOrderId);

        OrderDetail detail1 = new OrderDetail();
        detail1.setName("宫保鸡丁");
        detail1.setNumber(3);

        OrderDetail detail2 = new OrderDetail();
        detail2.setName("麻婆豆腐");
        detail2.setNumber(2);

        List<OrderDetail> orderDetailList = Arrays.asList(detail1, detail2);

        // Mock 依赖方法
        when(orderDetailMapper.getByOrderId(testOrderId)).thenReturn(orderDetailList);

        // 执行测试 (使用反射调用私有方法)
        String result = invokePrivateMethod("getOrderDishesStr", orders);

        // 验证结果
        assertEquals("宫保鸡丁*3;麻婆豆腐*2;", result);
        verify(orderDetailMapper).getByOrderId(testOrderId);
    }

    @Test
    void testGetOrderDishesStr_EmptyOrderDetails() {
        // 准备测试数据
        Orders orders = new Orders();
        orders.setId(testOrderId);

        // Mock 返回空列表
        when(orderDetailMapper.getByOrderId(testOrderId)).thenReturn(new ArrayList<>());

        // 执行测试
        String result = invokePrivateMethod("getOrderDishesStr", orders);

        // 验证结果
        assertEquals("", result);
        verify(orderDetailMapper).getByOrderId(testOrderId);
    }

    // repetition 方法的测试
    @Test
    void testRepetition_Success() {
        // 准备测试数据
        Long userId = 1L;
        Long orderId = testOrderId;

        OrderDetail detail1 = new OrderDetail();
        detail1.setId(46L);
        detail1.setName("宫保鸡丁");
        detail1.setNumber(3);
        detail1.setAmount(new BigDecimal("30.00"));

        OrderDetail detail2 = new OrderDetail();
        detail2.setId(47L);
        detail2.setName("麻婆豆腐");
        detail2.setNumber(2);
        detail2.setAmount(new BigDecimal("20.00"));

        List<OrderDetail> orderDetailList = Arrays.asList(detail1, detail2);

        // Mock 依赖方法
        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)){
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(userId);
            when(orderDetailMapper.getByOrderId(orderId)).thenReturn(orderDetailList);

            // 执行测试
            orderService.repetition(orderId);

            // 验证方法调用
            verify(orderDetailMapper).getByOrderId(orderId);
            verify(shoppingCartMapper).insertBatch(argThat(shoppingCartList -> {
                if (shoppingCartList.size() != 2) return false;

                ShoppingCart cart1 = shoppingCartList.get(0);
                ShoppingCart cart2 = shoppingCartList.get(1);

                return cart1.getName().equals("宫保鸡丁") &&
                        cart1.getNumber().equals(3) &&
                        cart1.getUserId().equals(userId) &&
                        cart1.getCreateTime() != null &&
                        cart2.getName().equals("麻婆豆腐") &&
                        cart2.getNumber().equals(2) &&
                        cart2.getUserId().equals(userId) &&
                        cart2.getCreateTime() != null;
            }));
        }

    }

    @Test
    void testRepetition_EmptyOrderDetails() {


        // 准备测试数据
        Long userId = 1L;
        Long orderId = testOrderId;

        // Mock 返回空列表
        try (MockedStatic<BaseContext> baseContextMock = mockStatic(BaseContext.class)) {
            baseContextMock.when(BaseContext::getCurrentId).thenReturn(userId);
            when(orderDetailMapper.getByOrderId(orderId)).thenReturn(new ArrayList<>());

            orderService.repetition(orderId);

            verify(orderDetailMapper).getByOrderId(orderId);
            verify(shoppingCartMapper).insertBatch(argThat(List::isEmpty));
        }
    }

    // 辅助方法：用于调用私有方法的反射工具
    private String invokePrivateMethod(String methodName, Orders orders) {
        try {
            Method method = orderService.getClass().getDeclaredMethod(methodName, Orders.class);
            method.setAccessible(true);
            return (String) method.invoke(orderService, orders);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke private method", e);
        }
    }
}
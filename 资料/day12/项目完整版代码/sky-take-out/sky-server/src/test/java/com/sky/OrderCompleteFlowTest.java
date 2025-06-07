package com.sky;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.Result;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.vo.UserLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TC2：正常订单完成流程黑盒测试
 * 测试完整的订单状态转换：创建订单 → 支付 → 接单 → 派送 → 完成
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class OrderCompleteFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private OrdersSubmitDTO orderSubmitDTO;
    private Long createdOrderId;
    private String orderNumber;
    private String userToken;


    @BeforeEach
    void setUp() {
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
        try {
            userToken = loginAndGetToken();
        }
        catch (Exception e){
            System.out.println(e);
        }

    }

    private String loginAndGetToken() throws Exception {
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

        return  token;
    }

    @Test
    @DisplayName("TC2: 正常订单完成流程 - 完整状态转换测试")
    void testCompleteOrderFlow() throws Exception {

        // ==================== 步骤1: 创建订单 ====================
        System.out.println("=== 步骤1: 创建订单 ===");

        MvcResult createResult = mockMvc.perform(post("/user/order/submit")
                        .header("authentication", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderSubmitDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.orderNumber").exists())
                .andReturn();

        // 提取创建的订单ID和订单号
        String createResponse = createResult.getResponse().getContentAsString();
        Result<OrderSubmitVO> createResultObj = objectMapper.readValue(createResponse,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));

        createdOrderId = createResultObj.getData().getId();
        orderNumber = createResultObj.getData().getOrderNumber();

        assertNotNull(createdOrderId, "订单ID不能为空");
        assertNotNull(orderNumber, "订单号不能为空");

        System.out.println("创建订单成功 - ID: " + createdOrderId + ", 订单号: " + orderNumber);

        // 验证初始状态：待付款(1)
        verifyOrderStatus(createdOrderId, 1, "创建订单后状态应为待付款");

        // ==================== 步骤2: 支付订单 ====================
        System.out.println("=== 步骤2: 支付订单 ===");

        OrdersPaymentDTO paymentDTO = new OrdersPaymentDTO();
        paymentDTO.setOrderNumber(orderNumber);
        paymentDTO.setPayMethod(1); // 微信支付

        mockMvc.perform(put("/user/order/payment")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        System.out.println("支付订单成功");

        // 验证支付后状态：待接单(2)
        verifyOrderStatus(createdOrderId, 2, "支付后状态应为待接单");

        // ==================== 步骤3: 商家接单 ====================
        System.out.println("=== 步骤3: 商家接单 ===");

        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
        confirmDTO.setId(createdOrderId);
        confirmDTO.setStatus(3); // 已接单

        mockMvc.perform(put("/admin/order/confirm")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        System.out.println("商家接单成功");

        // 验证接单后状态：已接单(3) - 对应您测试用例中的 "Preparing"
        verifyOrderStatus(createdOrderId, 3, "商家接单后状态应为已接单(准备中)");

        // ==================== 步骤4: 派送订单 ====================
        System.out.println("=== 步骤4: 派送订单 ===");

        mockMvc.perform(put("/admin/order/delivery/" + createdOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        System.out.println("开始派送成功");

        // 验证派送后状态：派送中(4) - 对应您测试用例中的 "Delivering"
        verifyOrderStatus(createdOrderId, 4, "开始派送后状态应为派送中");

        // ==================== 步骤5: 完成订单 ====================
        System.out.println("=== 步骤5: 完成订单 ===");

        mockMvc.perform(put("/admin/order/complete/" + createdOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        System.out.println("完成订单成功");

        // 验证完成后状态：已完成(5) - 对应您测试用例中的 "Completed"
        verifyOrderStatus(createdOrderId, 5, "完成订单后状态应为已完成");

        System.out.println("=== 订单完整流程测试通过 ===");
    }

    /**
     * 验证订单状态的辅助方法
     * @param orderId 订单ID
     * @param expectedStatus 期望的状态值
     * @param message 断言失败时的消息
     */
    private void verifyOrderStatus(Long orderId, Integer expectedStatus, String message) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/order/details/" + orderId))
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

        System.out.println("状态验证通过 - 当前状态: " + expectedStatus + " (" + getStatusName(expectedStatus) + ")");
    }

    /**
     * 获取状态名称的辅助方法
     */
    private String getStatusName(Integer status) {
        switch (status) {
            case 1: return "待付款";
            case 2: return "待接单";
            case 3: return "已接单";
            case 4: return "派送中";
            case 5: return "已完成";
            case 6: return "已取消";
            default: return "未知状态";
        }
    }

    @Test
    @DisplayName("TC2-边界验证: 验证每个状态转换的前置条件")
    void testStatusTransitionPreconditions() throws Exception {

        // 创建订单
        MvcResult createResult = mockMvc.perform(post("/user/order/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderSubmitDTO)))
                .andExpect(status().isOk())
                .andReturn();

        String createResponse = createResult.getResponse().getContentAsString();
        Result<OrderSubmitVO> createResultObj = objectMapper.readValue(createResponse,
                objectMapper.getTypeFactory().constructParametricType(Result.class, OrderSubmitVO.class));
        Long orderId = createResultObj.getData().getId();

        // 验证：未支付订单不能直接接单（应该失败）
        OrdersConfirmDTO confirmDTO = new OrdersConfirmDTO();
        confirmDTO.setId(orderId);
        confirmDTO.setStatus(3);

        MvcResult confirmResult = mockMvc.perform(put("/admin/order/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmDTO)))
                .andReturn();

        // 根据业务逻辑，这里可能返回错误或者被忽略
        // 验证订单状态仍然是待付款
        verifyOrderStatus(orderId, 1, "未支付的订单不应该能被接单");

        System.out.println("前置条件验证通过：未支付订单无法接单");
    }

    @Test
    @DisplayName("TC2-性能验证: 验证状态转换的响应时间")
    void testStatusTransitionPerformance() throws Exception {

        long startTime, endTime;

        // 测试创建订单的响应时间
        startTime = System.currentTimeMillis();

        MvcResult createResult = mockMvc.perform(post("/user/order/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderSubmitDTO)))
                .andExpect(status().isOk())
                .andReturn();

        endTime = System.currentTimeMillis();
        long createOrderTime = endTime - startTime;

        assertTrue(createOrderTime < 2000, "创建订单应在2秒内完成，实际用时: " + createOrderTime + "ms");

        System.out.println("性能测试通过 - 创建订单用时: " + createOrderTime + "ms");
    }
}
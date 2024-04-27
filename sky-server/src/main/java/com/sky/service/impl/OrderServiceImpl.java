package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.IdConstant;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;
    private Orders orders;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //——1.处理异常
        //地址簿为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //购物车为空
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if(shoppingCarts==null || shoppingCarts.size()==0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //——2.向订单表插入一条数据
        //构造Order对象
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        //补充数据
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        this.orders = orders;
        //插入
        orderMapper.insert(orders);
        List<OrderDetail> orderDetailList = new ArrayList<>();
        //——3.向订单明细表插入多条数据
        for (ShoppingCart cart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        //批量插入
        orderDetailMapper.insertBatch(orderDetailList);
        //——4.清空购物车
        shoppingCartMapper.deleteByUserId(userId);
        //——5.构造视图对象返回
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
        return orderSubmitVO;
    }
    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        /*//调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code","ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        //默认支付成功
        paySuccess(ordersPaymentDTO.getOrderNumber());
        return vo;

    }
    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        //——1.根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        //——2.根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        //——3.更新订单状态
        orderMapper.update(orders);
        //——4.向管理端发送来单提示
        //调用webSocketServer向管理端发送消息，前后端约定传递三个参数：type、orderId、content
        Map map = new HashMap();
        map.put("type",1);//1表示来单提醒，2表示客户催单
        map.put("orderId",ordersDB.getId());//订单id
        map.put("content","订单号："+outTradeNo);//订单号
        String message = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(message);
    }
    /**
     * 历史订单查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery(Integer page, Integer pageSize, Integer status){
        Long userId = BaseContext.getCurrentId();
        //——1.封装查询对象，设置分页查询
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setPage(page);
        ordersPageQueryDTO.setPageSize(pageSize);
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(userId);
        PageHelper.startPage(page,pageSize);
        //——2.查询订单表，返回订单列表
        Page<Orders> ordersPage = orderMapper.listByPage(ordersPageQueryDTO);
        List<OrderVO> orderVOList = new ArrayList<>();
        //——3.对订单列表的每个订单，查询订单明细表
        if(ordersPage!=null && ordersPage.size()>0){
            for (Orders orders1 : ordersPage) {
                List<OrderDetail> orderDetailList = orderDetailMapper.selectByOrderId(orders1.getId());
                OrderVO orderVO = new OrderVO();
                /*OrderVO类继承自Orders类，因此OrderVO类中实际是拥有有Orders类的所有属性
                *而显示声明的两个是属于OrderVO自己的
                * 所以要做属性拷贝设置OrderVO那些继承自Orders的属性值
                */
                BeanUtils.copyProperties(orders1,orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                orderVOList.add(orderVO);
            }
        }
        //——4.封装视图对象并返回
        return new PageResult(ordersPage.getTotal(),orderVOList);
    }

    /**
     * 查询订单详情接口
     * @param orderId
     * @param identity
     * @return
     */
    public OrderVO getOrderDetails(Long orderId,Integer identity){
        //查询订单表获取订单对象
        Orders orders = orderMapper.getById(orderId);
        //查询订单详情表获取订单详情列表
        List<OrderDetail> orderDetailList = orderDetailMapper.selectByOrderId(orderId);
        //构造视图对象
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        orderVO.setAddress(addressBookMapper.getById(orderVO.getAddressBookId()).getDetail());
        //对用户端查询订单操作，做姓名和号码部分隐藏处理
        if(identity==IdConstant.user){
            String name = orderVO.getConsignee();
            String phone = orderVO.getPhone();
            StringBuilder sb = new StringBuilder();
            sb.append(name.charAt(0));
            for(int i=1;i<name.length();i++){
                sb.append("*");
            }
            phone = phone.substring(0,3)+"****"+phone.substring(7,phone.length());
            orderVO.setConsignee(sb.toString());
            orderVO.setPhone(phone);
        }
        return orderVO;
    }
    /**
     * 用户取消订单
     * @param orderId
     */
    public void userCancelOrder(Long orderId){
        /*-待支付和待接单状态下，用户可直接取消订单
          - 商家已接单状态下，用户取消订单需电话沟通商家
          - 派送中状态下，用户取消订单需电话沟通商家
          - 如果在待接单状态下取消订单，需要给用户退款
          - 取消订单后需要将订单状态修改为“已取消”
        */
        //——1.取出订单，查看订单是否存在
        Orders orders = orderMapper.getById(orderId);
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Integer status = orders.getStatus();
        //——2.查询订单状态
        //若为已接单状态，抛异常
        if(status>Orders.CONFIRMED){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //创建退单对象，用于更新表中已取消的订单
        Orders cancelOrder = new Orders();
        cancelOrder.setId(orderId);
        //若为待接单状态，调用微信接口退款，并修改订单状态
        if (orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            /*//调用微信支付退款接口
            weChatPayUtil.refund(
                    orders.getNumber(), //商户订单号
                    orders.getNumber(), //商户退款单号
                    new BigDecimal(0.01),//退款金额，单位 元
                    new BigDecimal(0.01));//原订单金额*/

            //支付状态修改为 退款
            cancelOrder.setPayStatus(Orders.REFUND);
        }
        //——4.补充已取消订单对象的信息，并替换原订单数据
        cancelOrder.setStatus(Orders.CANCELLED);
        cancelOrder.setCancelReason("用户取消");
        cancelOrder.setCancelTime(LocalDateTime.now());
        orderMapper.update(cancelOrder);
    }
    /**
     * 再来一单
     * @param orderId
     */
    public void repeat(Long orderId){
        Long userId = BaseContext.getCurrentId();
        //——1.查询订单详情表，获取数据列表
        List<OrderDetail> orderDetailList = orderDetailMapper.selectByOrderId(orderId);
        //——2.将数据封装到购物车列表中
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail,shoppingCart);
            //不要把id也拷贝过去
            shoppingCart.setId(null);
            //补充购物车数据信息：userId、createTime
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartList.add(shoppingCart);
        }
        //——3.插入购物车表
        shoppingCartMapper.insertBatch(shoppingCartList);
    }
    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO){
        //——1.分页查询订单表，获取数据列表
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> ordersPage = orderMapper.listByPage(ordersPageQueryDTO);
        List<OrderVO> orderVOList = new ArrayList<>();
        //——2.将订单列表转成OrderVO列表
        for (Orders orders1 : ordersPage) {
            //构造orderVO对象
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders1,orderVO);
            //根据订单id获取订单详情数据和订单菜品信息
            List<OrderDetail> orderDetailList = orderDetailMapper.selectByOrderId(orders1.getId());
            orderVO.setOrderDetailList(orderDetailList);
            orderVO.setOrderDishes(getOrderDishes(orderDetailList));
            orderVO.setAddress(addressBookMapper.getById(orderVO.getAddressBookId()).getDetail());
            orderVOList.add(orderVO);
        }
        return new PageResult(ordersPage.getTotal(),orderVOList);
    }

    /**
     * 根据订单详情获取订单菜品信息
     * @param orderDetailList
     * @return
     */
    public String getOrderDishes(List<OrderDetail> orderDetailList){
        StringBuilder sb = new StringBuilder();
        for (OrderDetail orderDetail : orderDetailList) {
            sb.append(orderDetail.getName()+"*"+orderDetail.getNumber()+";");
        }
        return sb.toString();
    }
    /**
     * 各个状态的订单数量统计
     * @return
     */
    public OrderStatisticsVO statics(){
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }
    /**
     * 接单
     * @param ordersConfirmDTO
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO){
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }
    /**
     * 商家拒单
     * @param ordersRejectionDTO
     */
    public void reject(OrdersRejectionDTO ordersRejectionDTO){
        //——1.根据订单号查询订单
        Long orderId = ordersRejectionDTO.getId();
        String rejectReason = ordersRejectionDTO.getRejectionReason();
        Orders orders = orderMapper.getById(orderId);
        //——2.查询订单状态，只有订单处于待接单状态才能取消
        if(orders==null || !orders.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //——3.查询订单支付状态，若订单已支付，则需要退款
        if(orders.getPayStatus().equals(Orders.PAID)){
            log.info("商家为用户退款");
        }
        //——4.更新订单状态
        Orders rejectionOrder = new Orders();
        rejectionOrder.setId(orders.getId());
        rejectionOrder.setStatus(Orders.CANCELLED);
        rejectionOrder.setRejectionReason(rejectReason);
        rejectionOrder.setCancelTime(LocalDateTime.now());
        orderMapper.update(rejectionOrder);
    }
    /**
     * 商家取消订单
     * @param ordersCancelDTO
     */
    public void adminCancelOrder(OrdersCancelDTO ordersCancelDTO){
        Long orderId = ordersCancelDTO.getId();
        String cancelReason = ordersCancelDTO.getCancelReason();
        //——1.查询要取消的订单
        Orders orders = orderMapper.getById(orderId);
        if(orders==null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //——2.查询订单支付状态，看是否需要退款
        if(orders.getPayStatus().equals(Orders.PAID)){
            log.info("商家为用户退款");
        }
        //——3.更新订单状态
        Orders cancelOrders = new Orders();
        cancelOrders.setId(orderId);
        cancelOrders.setStatus(Orders.CANCELLED);
        cancelOrders.setCancelReason(cancelReason);
        cancelOrders.setCancelTime(LocalDateTime.now());
        orderMapper.update(cancelOrders);
    }
    /**
     * 派送订单
     * @param orderId
     */
    public void deliver(Long orderId){
        //——1.取出订单
        Orders orders = orderMapper.getById(orderId);
        //——2.判断：订单是否存在、订单是否处于已接单状态
        if(orders==null || !orders.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //——3.修改订单状态
        Orders deliveryOrder = new Orders();
        deliveryOrder.setId(orderId);
        deliveryOrder.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(deliveryOrder);
    }
    /**
     * 完成订单
     * @param orderId
     */
    public void complete(Long orderId){
        //——1.取出订单
        Orders orders = orderMapper.getById(orderId);
        //——2.只有订单存在且处于派送中，才能完成
        if(orders==null || !orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //——3.修改订单状态
        Orders completeOrder = new Orders();
        completeOrder.setId(orderId);
        completeOrder.setStatus(Orders.COMPLETED);
        completeOrder.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(completeOrder);
    }
}

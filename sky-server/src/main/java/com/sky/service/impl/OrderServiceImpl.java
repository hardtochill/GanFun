package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        Integer OrderPaidStatus = Orders.PAID;//支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单
        LocalDateTime check_out_time = LocalDateTime.now();//更新支付时间
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, this.orders.getId());
        return vo;

    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
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
     * @return
     */
    public OrderVO getOrderDetails(Long orderId){
        //查询订单表获取订单对象
        Orders orders = orderMapper.getById(orderId);
        //查询订单详情表获取订单详情列表
        List<OrderDetail> orderDetailList = orderDetailMapper.selectByOrderId(orderId);
        //构造视图对象返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }
    /**
     * 取消订单
     * @param orderId
     */
    public void cancelOrder(Long orderId){
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
}

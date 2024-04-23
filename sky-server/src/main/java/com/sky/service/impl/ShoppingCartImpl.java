package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    /**
     * 添加购物车，每次添加的商品要么为dish，要么为setmeal
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //——查看购物车中是否有相同商品
        //构造查询对象，需要补充userId
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        //查询需要根据userId、dishId、setmealId、dishFlavor查询
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        //——如果有，则更新数量加1
        if(list!=null && list.size()>0){
            //实际上查出来的商品列表要么没有，要么只有一个——就是对应商品
            ShoppingCart shoppingCart1 = list.get(0);
            shoppingCart1.setNumber(shoppingCart1.getNumber()+1);
            //根据id更新数量
            shoppingCartMapper.updateNumberById(shoppingCart1);
        }else{
            //——如果没有，则插入商品
            //判断要插入的是dish还是setmeal
            Long dishId = shoppingCart.getDishId();
            if(dishId!=null){
                //要插入的是dish，查出对应dish，并构造插入数据
                Dish dish = dishMapper.selectById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setAmount(dish.getPrice());
                shoppingCart.setImage(dish.getImage());
            }else{
                //要插入的是setmeal，查出对应setmeal，并构造擦汗如数据
                Long setmealId = shoppingCart.getSetmealId();
                Setmeal setmeal = setmealMapper.selectById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setAmount(setmeal.getPrice());
                shoppingCart.setImage(setmeal.getImage());
            }
            //补充相同信息
            shoppingCart.setNumber(1);//数量都是1份
            shoppingCart.setCreateTime(LocalDateTime.now());
            //插入
            shoppingCartMapper.insert(shoppingCart);
        }
    }
    /**
     * 查看购物车
     * @return
     */
    public List<ShoppingCart> showShoppingCart(){
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        return list;
    }
    /**
     * 清空购物车
     */
    public void clean(){
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }
    /**
     * 删除购物车中一件商品
     * @param shoppingCartDTO
     */
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO){
        //——判断要删除的数据是否还在购物车中
        //构造查询对象
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO,shoppingCart);
        //补充信息
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        //——如果有，判断该商品的数量
        if(list!=null && list.size()>0){
            shoppingCart = list.get(0);
            if(shoppingCart.getNumber()==1){
                //商品数量只有一件，直接删除
                shoppingCartMapper.deleteById(shoppingCart.getId());
            }else{
                //商品数量有多件，修改数量：-1
                shoppingCart.setNumber(shoppingCart.getNumber()-1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            }
        }
    }

    /**
     * 根据主键id删除数据
     * @param id
     */
    public void deleteById(Long id){
        shoppingCartMapper.deleteById(id);
    }
}

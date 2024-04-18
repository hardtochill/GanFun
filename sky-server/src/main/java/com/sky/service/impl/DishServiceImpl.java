package com.sky.service.impl;

import com.sky.dto.DishDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.DishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 菜品管理业务层
 */
@Service
@Slf4j
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    /**
     * 新增菜品
     * 由于要同时向两张表插入数据，因此开启事务
     * @param dishDTO
     */
    @Transactional
    @Override
    public void saveWithFlavor(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        //向菜品表插入数据，插入后会把dish在表中的id字段赋值给dish对象的id属性
        dishMapper.insert(dish);
        Long dishId = dish.getId();
        //向口味表中插入多条数据
        List<DishFlavor> dishFlavorList = dishDTO.getFlavors();
        if(dishFlavorList!=null && dishFlavorList.size()>0){
            //为每个口味元素绑定它们的菜品id
            for (DishFlavor dishFlavor : dishFlavorList) {
                dishFlavor.setDishId(dishId);
            }
            dishFlavorMapper.insertBatch(dishFlavorList);
        }
    }
}

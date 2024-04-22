package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetMealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    @Autowired
    private SetMealDishMapper setMealDishMapper;
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

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    public DishVO getByIdWithFlavor(Long id){
        //根据id查询菜品数据
        Dish dish =  dishMapper.selectById(id);
        //根据口味id查询口味数据
        List<DishFlavor> dishFlavors = dishFlavorMapper.selectByDishId(id);
        //设置dishVO对象
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish,dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    public List<Dish> listByCategoryId(Long categoryId){
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        return dishMapper.list(dish);
    }

    /**
     * 分页查询菜品
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult listByPage(DishPageQueryDTO dishPageQueryDTO){
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO> dishVOPage = dishMapper.listByPage(dishPageQueryDTO);
        return new PageResult(dishVOPage.getTotal(),dishVOPage.getResult());
    }
    /**
     * 批量删除菜品,只有有一道菜品不能删除则都不删除
     * @param ids
     */
    @Transactional
    public void deleteBatch(List<Long> ids){
        //判断菜品是否可以删除——菜品当前状态？？
        for (Long id : ids) {
            Dish dish = dishMapper.selectById(id);
            if(dish.getStatus() == StatusConstant.ENABLE){
                //菜品在售中，不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //判断菜品是否可以删除——菜品是否关联套餐表？？
        List<Long> setMealIds = setMealDishMapper.selectSetMealIdsByDishIds(ids);
        if(setMealIds!=null && setMealIds.size()>0){
            //菜品关联了套餐，不能删除
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        //批量删除菜品
        /*for (Long id : ids) {
            dishMapper.deleteById(id);
            //删除口味表中已删除菜品对应的口味
            dishFlavorMapper.deleteByDishId(id);
        }*/
        dishMapper.deleteByIds(ids);
        dishFlavorMapper.deleteByDishIds(ids);
    }
    /**
     * 修改菜品
     * @param dishDTO
     */
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO){
        //修改菜品表
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.update(dish);
        Long dishId = dishDTO.getId();
        //删除口味表中菜品原有口味数据
        dishFlavorMapper.deleteByDishId(dishId);
        //添加菜品新口味
        List<DishFlavor> dishFlavorList = dishDTO.getFlavors();
        if(dishFlavorList!=null && dishFlavorList.size()>0){
            //为每个口味元素绑定它们的菜品id
            for (DishFlavor dishFlavor : dishFlavorList) {
                dishFlavor.setDishId(dishId);
            }
            dishFlavorMapper.insertBatch(dishFlavorList);
        }
    }

    /**
     * 菜品起售停售
     * @param id
     * @param status
     */
    public void updateStatus(Long id,int status){
        Dish dish = new Dish();
        dish.setId(id);
        dish.setStatus(status);
        dishMapper.update(dish);
    }
    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    public List<DishVO> listWithFlavor(Dish dish) {
        List<Dish> dishList = dishMapper.list(dish);

        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d,dishVO);

            //根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.selectByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}



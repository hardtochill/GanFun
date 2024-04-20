package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.SetmealDish;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetMealDishMapper {
    /**
     * 根据套餐id查询菜品id
     * @param dishids
     * @return
     */
    List<Long> selectSetMealIdsByDishIds(List<Long> dishids);

    /**
     * 批量插入套餐-菜品数据
     * @param setmealDishList
     */
    void insertBatch(List<SetmealDish> setmealDishList);
    /**
     * 根据套餐id删除数据
     */
    @Delete("delete from setmeal_dish where setmeal_id=#{setmealId}")
    void deleteBySetmealId(Long setmealId);
    /**
     * 根据套餐id批量删除数据
     * @param setmealIds
     */
    void deleteBySetmealIds(List<Long> setmealIds);

    /**
     * 根据套餐id查询所有菜品
     * @param setmealId
     * @return
     */
    @Select("select * from setmeal_dish where setmeal_id=#{setmealId}")
    List<SetmealDish> selectBySetmealId(Long setmealId);
}

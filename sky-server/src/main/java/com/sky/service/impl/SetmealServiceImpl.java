package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.annotation.AutoFill;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetMealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetMealDishMapper setMealDishMapper;
    /**
     * 新增套餐
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        //在套餐表中插入数据
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.save(setmeal);
        Long setmealId = setmeal.getId();
        //在套餐-菜品表中批量插入数据
        List<SetmealDish> setmealDishList = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : setmealDishList) {
            setmealDish.setSetmealId(setmealId);
        }
        setMealDishMapper.insertBatch(setmealDishList);
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult listByPageWithCategoryName(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(),setmealPageQueryDTO.getPageSize());
        //查询套餐表信息、查询分类表名字
        Page<SetmealVO> setmealVOPage = setmealMapper.listByPageWithCategoryName(setmealPageQueryDTO);
        return new PageResult(setmealVOPage.getTotal(),setmealVOPage.getResult());
    }

    /**
     * 批量删除套餐
     * @param ids
     */
    @Transactional
    public void deleteByIds(List<Long> ids){
        if(ids!=null && ids.size()>0){
            //查看套餐能否删除——是否在售？？
            for (Long id : ids) {
                Setmeal setmeal = setmealMapper.selectById(id);
                if(setmeal.getStatus()== StatusConstant.ENABLE){
                    throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
                }
            }
            //删除套餐表数据
            setmealMapper.deleteByIds(ids);
            //删除套餐-菜品表数据
            setMealDishMapper.deleteBySetmealIds(ids);
        }else{
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_DOESNT_EXIT);
        }
    }
    /**
     * 根据id查询套餐
     * @param id
     * @return
     */
    public SetmealVO getByIdWithCategoryNameAndDishes(Long id){
        //查询套餐表信息 + 分类表名字字段
        SetmealVO setmealVO = setmealMapper.selectByIdWithCategoryname(id);
        //查询套餐-菜品表信息
        List<SetmealDish> setmealDishList = setMealDishMapper.selectBySetmealId(setmealVO.getId());
        setmealVO.setSetmealDishes(setmealDishList);
        return setmealVO;
    }
    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Transactional
    public void update(SetmealDTO setmealDTO){
        //修改套餐表信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.update(setmeal);
        //修改套餐菜品表信息
        List<SetmealDish> setmealDishList = setmealDTO.getSetmealDishes();
        if(setmealDishList!=null && setmealDishList.size()>0){
            Long setmealId = setmealDTO.getId();
            //先删除原套餐所有关联数据
            setMealDishMapper.deleteBySetmealId(setmealId);
            //为每个套餐-菜品元素绑定套餐id
            for (SetmealDish setmealDish : setmealDishList) {
                setmealDish.setSetmealId(setmealId);
            }
            //插入新的套餐-菜品数据
            setMealDishMapper.insertBatch(setmealDishList);
        }
    }
    /**
     * 套餐起售停售
     * @param id
     * @param status
     */
    public void startOrstop(Long id, int status){
        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.update(setmeal);
    }
    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }
    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

}

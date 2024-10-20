package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisCache;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    ShopMapper shopMapper;

    @Resource
    RedisCache redisCache;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7.返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result myUpdate(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);

        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }

//    @Override
//    public Shop queryWithMutex(Long id) {
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        if (StrUtil.isNotBlank(shopJson)){
//            return JSONUtil.toBean(shopJson,Shop.class);
//        }
//        if (shopJson!=null){
//            return null;
//        }
//
//        Shop shop;
//        try{
//            boolean isLock=redisCache.tryLock(LOCK_SHOP_KEY+id);
//            if (!isLock){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//             shop = getById(id);
//            if (shop==null) {
//                stringRedisTemplate.opsForValue().set(LOCK_SHOP_KEY+id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set(LOCK_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_NULL_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            redisCache.unlock(LOCK_SHOP_KEY+id);
//        }
//        return shop;
//    }

//    @Override
//    public void saveShop2Redis(Long id, Long expireSeconds) {
//        Shop shop=getById(id);
//        RedisData redisData=new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire( Long id ) {
//        String key = CACHE_SHOP_KEY + id;
//        // 1.从redis查询商铺缓存
//        String json = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if (StrUtil.isBlank(json)) {
//            // 3.存在，直接返回
//            return null;
//        }
//        // 4.命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())) {
//            // 5.1.未过期，直接返回店铺信息
//            return shop;
//        }
//        // 5.2.已过期，需要缓存重建
//        // 6.缓存重建
//        // 6.1.获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = redisCache.tryLock(lockKey);
//        // 6.2.判断是否获取锁成功
//        if (isLock){
//            CACHE_REBUILD_EXECUTOR.submit( ()->{
//
//                try{
//                    //重建缓存
//                    this.saveShop2Redis(id,20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    redisCache.unlock(lockKey);
//                }
//            });
//        }
//        // 6.4.返回过期的商铺信息
//        return shop;
//    }
@Override
public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
    //1. 判断是否需要根据距离查询
    if (x == null || y == null) {
        // 根据类型分页查询
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
    //2. 计算分页查询参数
    int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
    int end = current * SystemConstants.MAX_PAGE_SIZE;
    String key = SHOP_GEO_KEY + typeId;
    //3. 查询redis、按照距离排序、分页; 结果：shopId、distance
    //GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m WITHDIST
    GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
            GeoReference.fromCoordinate(x, y),
            new Distance(5000),
            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
    if (results == null) {
        return Result.ok(Collections.emptyList());
    }
    //4. 解析出id
    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
    if (list.size() < from) {
        //起始查询位置大于数据总量，则说明没数据了，返回空集合
        return Result.ok(Collections.emptyList());
    }
    ArrayList<Long> ids = new ArrayList<>(list.size());
    HashMap<String, Distance> distanceMap = new HashMap<>(list.size());
    list.stream().skip(from).forEach(result -> {
        String shopIdStr = result.getContent().getName();
        ids.add(Long.valueOf(shopIdStr));
        Distance distance = result.getDistance();
        distanceMap.put(shopIdStr, distance);
    });
    //5. 根据id查询shop
    String idsStr = StrUtil.join(",", ids);
    List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD( id," + idsStr + ")").list();
    for (Shop shop : shops) {
        //设置shop的举例属性，从distanceMap中根据shopId查询
        shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
    }
    //6. 返回
    return Result.ok(shops);
}
}

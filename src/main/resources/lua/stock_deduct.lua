-- 库存扣减Lua脚本
-- KEYS[1]: 库存的Redis Key，格式：seckill:stock:{goodsId}
        -- ARGV[1]: 扣减数量，默认为1
-- 返回值：1-扣减成功，0-库存不足，-1-库存Key不存在

-- 获取当前库存
local stock = redis.call('get', KEYS[1])

        -- 判断库存Key是否存在
if not stock then
    return -1
end

-- 转换为数字
local stockNum = tonumber(stock)

        -- 获取扣减数量
local deductNum = tonumber(ARGV[1])
if not deductNum then
        deductNum = 1
end

-- 判断库存是否充足
if stockNum < deductNum then
    return 0
end

-- 扣减库存
redis.call('decrby', KEYS[1], deductNum)

-- 返回成功
return 1
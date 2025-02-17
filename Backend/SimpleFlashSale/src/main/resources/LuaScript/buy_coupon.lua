local couponKey = KEYS[1]  -- Coupon inventory key
local userOrderSetKey = KEYS[2]  -- User's unpaid order set
local unpaidOrdersKey = KEYS[3]  -- Global unpaid orders set

local userId = ARGV[1]
local orderId = ARGV[2]
local currentTime = ARGV[3]

-- Check inventory
local inventory = tonumber(redis.call("GET", couponKey))
if not inventory or inventory <= 0 then
    return -1  -- Inventory not enough
end

-- Check if user already ordered
if redis.call("SISMEMBER", userOrderSetKey, orderId) == 1 then
    return -2  -- User already ordered
end

-- Deduct inventory
redis.call("DECR", couponKey)

-- Add user order
redis.call("SADD", userOrderSetKey, orderId)

-- Add order to unpaid orders sorted set
redis.call("ZADD", unpaidOrdersKey, currentTime, orderId)

return 1  -- Success

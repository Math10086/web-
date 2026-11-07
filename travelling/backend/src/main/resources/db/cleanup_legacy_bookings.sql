SET NAMES utf8;

-- 归一化向导预约的历史脏数据：
-- guide_bookings 的合法状态机为 PAID → CONFIRMED → SETTLED（完成，可结算）/ CANCELLED，
-- 早期演示数据曾把「已完成」误写成结伴用的 FINISHED，导致：
--   1) 接单列表按状态筛选时查不到这些订单；
--   2) 管理员「交易结算」的导游侧统计（只取 SETTLED）漏掉它们。
-- 下面把已完成订单统一改回 SETTLED，并补算此前未结转的平台抽成与导游收入（按 10%）。
UPDATE guide_bookings
SET status = 'SETTLED'
WHERE status = 'FINISHED';

UPDATE guide_bookings
SET commission_rate = 0.10,
    fee            = ROUND(amount * 0.10, 2),
    guide_income   = ROUND(amount * 0.90, 2)
WHERE status = 'SETTLED'
  AND (fee IS NULL OR fee = 0);

SELECT status, COUNT(*) AS cnt FROM guide_bookings GROUP BY status ORDER BY status;

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:9090'
$jar = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\ruoyi-admin.jar'
$log = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke3.log'
$errlog = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke3.err.log'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.3\bin\mysql.exe'
$fail = 0

function Check($name, $cond) {
    if ($cond) { Write-Output ("PASS " + $name) }
    else { Write-Output ("FAIL " + $name); $script:fail++ }
}

$p = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$jar`"", '--shiro.user.captchaEnabled=false') -RedirectStandardOutput $log -RedirectStandardError $errlog -PassThru -WindowStyle Hidden
Write-Output "PID=$($p.Id)"
try {
    $up = $false
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        if ($p.HasExited) { break }
        try {
            $r = Invoke-WebRequest -Uri "$base/login" -SessionVariable s -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { $up = $true; break }
        } catch {}
    }
    if (-not $up) { Write-Output 'APP_NOT_UP'; exit 1 }

    $null = Invoke-WebRequest -Uri "$base/login" -Method Post -Body @{ username='admin'; password='admin123'; rememberme='false' } -WebSession $s -UseBasicParsing

    function Post($path, $data) {
        $resp = Invoke-WebRequest -Uri ($base + $path) -Method Post -Body $data -WebSession $s -UseBasicParsing -Headers @{ 'X-Requested-With' = 'XMLHttpRequest' }
        return ($resp.Content | ConvertFrom-Json)
    }

    # 准备：采购商 + 站点
    $r1 = Post '/gas/buyer/add' @{ buyerName='冒烟3采购商'; shortName='冒烟3'; notifyPhones='13800000000'; status='0' }
    $blist = Post '/gas/buyer/list' @{}
    $buyer = $blist.rows | Where-Object { $_.buyerName -eq '冒烟3采购商' }
    Check 'BUYER_ADD' ($r1.code -eq 0 -and $null -ne $buyer)

    $r2 = Post '/gas/station/add' @{ stationCode='SMK3'; stationName='冒烟3站点'; buyerId=$buyer.buyerId; status='0' }
    $slist = Post '/gas/station/list' @{}
    $station = $slist.rows | Where-Object { $_.stationCode -eq 'SMK3' }
    Check 'STATION_ADD' ($r2.code -eq 0 -and $null -ne $station)

    # 1. 采购批次（qty 200 x 3.0000 = amount 600）
    $r3 = Post '/gas/purchase/batch/add' @{ batchNo='SMK3-B01'; buyerId=$buyer.buyerId; stationId=$station.stationId; gasType='CNG'; purchaseDate='2026-11-16'; qty='200'; unitPrice='3.0000'; amount='600.00' }
    Check 'BATCH_ADD' ($r3.code -eq 0)
    $batList = Post '/gas/purchase/batch/list' @{ batchNo='SMK3-B01' }
    $batch = $batList.rows | Where-Object { $_.batchNo -eq 'SMK3-B01' }
    Check 'BATCH_FOUND' ($null -ne $batch)

    # 2. 入库 x2
    $r4 = Post '/gas/purchase/receipt/add' @{ batchId=$batch.batchId; qty='100'; receivedAt='2026-11-16 08:00:00' }
    $r5 = Post '/gas/purchase/receipt/add' @{ batchId=$batch.batchId; qty='100'; receivedAt='2026-11-16 12:00:00' }
    Check 'RECEIVE_X2' ($r4.code -eq 0 -and $r5.code -eq 0)

    # 3. 批次列表 join 聚合字段（L1 回归）
    $batList = Post '/gas/purchase/batch/list' @{ batchNo='SMK3-B01' }
    $batch = $batList.rows | Where-Object { $_.batchNo -eq 'SMK3-B01' }
    Check 'BATCH_RECEIVED_QTY_JOIN' ([decimal]$batch.receivedQty -eq 200)
    Check 'BATCH_PAID_AMOUNT_JOIN' ([decimal]$batch.paidAmount -eq 0)
    Check 'BATCH_PAY_UNPAID' ($batch.payStatus -eq 'UNPAID')

    # 4. 付款两笔 -> PARTIAL -> PAID（M1 回归）
    $r6 = Post '/gas/purchase/payment/add' @{ batchId=$batch.batchId; amount='300.00'; paidAt='2026-11-16'; payMethod='BANK' }
    Check 'PAYMENT_ADD_1' ($r6.code -eq 0)
    $batList = Post '/gas/purchase/batch/list' @{ batchNo='SMK3-B01' }
    $batch = $batList.rows | Where-Object { $_.batchNo -eq 'SMK3-B01' }
    Check 'BATCH_PAY_PARTIAL' ($batch.payStatus -eq 'PARTIAL' -and [decimal]$batch.paidAmount -eq 300)

    $r7 = Post '/gas/purchase/payment/add' @{ batchId=$batch.batchId; amount='300.00'; paidAt='2026-11-16'; payMethod='BANK' }
    $batList = Post '/gas/purchase/batch/list' @{ batchNo='SMK3-B01' }
    $batch = $batList.rows | Where-Object { $_.batchNo -eq 'SMK3-B01' }
    Check 'BATCH_PAY_PAID' ($batch.payStatus -eq 'PAID' -and [decimal]$batch.paidAmount -eq 600)

    # 5. 库存调整 -5
    $plist = Post '/gas/inventory/pool/list' @{}
    $pool = $plist.rows | Where-Object { $_.stationId -eq $station.stationId -and $_.gasType -eq 'CNG' }
    Check 'POOL_FOUND' ($null -ne $pool)
    $receiptsPath = "/gas/inventory/pool/receipts/$($pool.poolId)"
    $rresp = Invoke-WebRequest -Uri ($base + $receiptsPath) -WebSession $s -UseBasicParsing
    $rlist = ($rresp.Content | ConvertFrom-Json)
    $receipt = ($rlist.data | Select-Object -First 1)
    $r8 = Post '/gas/inventory/adjust' @{ poolId=$pool.poolId; receiptId=$receipt.receiptId; qty='-5'; reason='冒烟计量误差' }
    $r9 = Post '/gas/inventory/adjust' @{ poolId=$pool.poolId; receiptId=$receipt.receiptId; qty='-1'; reason='' }
    Check 'ADJUST_OK_AND_EMPTY_REASON_REJECTED' ($r8.code -eq 0 -and $r9.code -ne 0)

    $plist = Post '/gas/inventory/pool/list' @{}
    $pool = $plist.rows | Where-Object { $_.stationId -eq $station.stationId -and $_.gasType -eq 'CNG' }
    Check 'POOL_BALANCE' ([decimal]$pool.totalIn -eq 200 -and [decimal]$pool.adjusted -eq -5)

    # 6. 流水列表（IN x2 + ADJUST）
    $llist = Post '/gas/inventory/ledger/list' @{ stationId=$station.stationId }
    $inCnt = ($llist.rows | Where-Object { $_.bizType -eq 'IN' }).Count
    $adjCnt = ($llist.rows | Where-Object { $_.bizType -eq 'ADJUST' }).Count
    Check 'LEDGER_ROWS' ($inCnt -eq 2 -and $adjCnt -eq 1)

    # 7. 提醒列表（字典值筛选回归 L3）
    $rmlist = Post '/gas/remind/list' @{ remindType='BATCH_EMPTY' }
    Check 'REMIND_LIST_BY_DICT' ($rmlist.code -eq 0)

    # 8. 页面渲染
    foreach ($pg in @('/gas/purchase/batch', '/gas/purchase/receipt', '/gas/purchase/payment', '/gas/inventory/pool', '/gas/inventory/ledger', '/gas/remind', ('/gas/inventory/adjust/' + $pool.poolId))) {
        try {
            $resp = Invoke-WebRequest -Uri ($base + $pg) -WebSession $s -UseBasicParsing
            Check ("PAGE " + $pg) ($resp.StatusCode -eq 200)
        } catch {
            Check ("PAGE " + $pg) $false
        }
    }

    Write-Output ("RESULT FAIL_COUNT=" + $fail)
}
finally {
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 1
    $l = Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue
    if ($l) { $l | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } }
    # JDBC 清理冒烟数据
    & $mysql -uroot -proot ry --default-character-set=utf8mb4 -e "delete from gas_remind where station_id in (select station_id from gas_station where station_code='SMK3'); delete from gas_inventory_ledger where pool_id in (select pool_id from gas_inventory_pool where station_id in (select station_id from gas_station where station_code='SMK3')); delete from gas_purchase_receipt where station_id in (select station_id from gas_station where station_code='SMK3'); delete from gas_inventory_pool where station_id in (select station_id from gas_station where station_code='SMK3'); delete from gas_purchase_payment where batch_id in (select batch_id from gas_purchase_batch where batch_no='SMK3-B01'); delete from gas_purchase_batch where batch_no='SMK3-B01'; delete from sys_dept where dept_name='冒烟3站点'; delete from gas_station where station_code='SMK3'; delete from gas_buyer_merchant where merchant_no='SMOKE886'; delete from gas_buyer where buyer_name='冒烟3采购商';" 2>$null
    Write-Output 'CLEANED'
}
exit $fail

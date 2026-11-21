$ErrorActionPreference = 'Stop'
$base = 'http://localhost:9090'
$jar = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\ruoyi-admin.jar'
$log = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke.log'
$errlog = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke.err.log'

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
    $idx = Invoke-WebRequest -Uri "$base/index" -WebSession $s -UseBasicParsing
    Write-Output ("LOGIN_OK=" + ($idx.StatusCode -eq 200))

    function Post($path, $data) {
        $resp = Invoke-WebRequest -Uri ($base + $path) -Method Post -Body $data -WebSession $s -UseBasicParsing
        return ($resp.Content | ConvertFrom-Json)
    }

    # 1. buyer
    $r1 = Post '/gas/buyer/add' @{ buyerName='冒烟测试采购商'; shortName='冒烟'; notifyPhones='13800000000'; invoiceRemindRatio='20'; invoiceRemindDays='7'; invoiceRemindMax='3'; status='0'; 'merchant.merchantNo'='SMOKE886' }
    Write-Output ("BUYER_ADD code=" + $r1.code + " msg=" + $r1.msg)
    $list = Post '/gas/buyer/list' @{}
    $buyer = $list.rows | Where-Object { $_.buyerName -eq '冒烟测试采购商' }
    Write-Output ("BUYER_ID=" + $buyer.buyerId)

    # 2. station
    $r2 = Post '/gas/station/add' @{ stationCode='SMK01'; stationName='冒烟测试站点'; buyerId=$buyer.buyerId; status='0' }
    Write-Output ("STATION_ADD code=" + $r2.code + " msg=" + $r2.msg)
    $slist = Post '/gas/station/list' @{}
    $station = $slist.rows | Where-Object { $_.stationCode -eq 'SMK01' }
    Write-Output ("STATION_ID=" + $station.stationId + " DEPT_ID=" + $station.deptId + " QR_TOKEN_LEN=" + $station.qrToken.Length)

    # qrcode download
    $qr = Invoke-WebRequest -Uri "$base/gas/station/qrcode/$($station.stationId)" -WebSession $s -UseBasicParsing
    Write-Output ("QRCODE bytes=" + $qr.RawContentLength + " type=" + $qr.Headers.'Content-Type')

    # 3. gun
    $r3 = Post '/gas/gun/add' @{ stationId=$station.stationId; gunNo='1'; gasType='CNG'; status='0' }
    Write-Output ("GUN_ADD code=" + $r3.code)
    $glist = Post '/gas/gun/list' @{}
    $gun = $glist.rows | Where-Object { $_.stationId -eq $station.stationId -and $_.gunNo -eq 1 }
    Write-Output ("GUN_ID=" + $gun.gunId)

    # duplicate gun guard
    $r3b = Post '/gas/gun/add' @{ stationId=$station.stationId; gunNo='1'; gasType='CNG'; status='0' }
    Write-Output ("GUN_DUP code=" + $r3b.code + " msg=" + $r3b.msg)

    # 4. price x2
    $r4 = Post '/gas/price/add' @{ gunId=$gun.gunId; price='3.5000'; effectiveFrom='2026-11-16 00:00:00'; remark='冒烟' }
    Write-Output ("PRICE_ADD1 code=" + $r4.code + " msg=" + $r4.msg)
    $r4b = Post '/gas/price/add' @{ gunId=$gun.gunId; price='3.8000'; effectiveFrom='2026-11-16 12:00:00'; remark='调价' }
    Write-Output ("PRICE_ADD2 code=" + $r4b.code + " msg=" + $r4b.msg)
    $plist = Post '/gas/price/list' @{}
    Write-Output ("PRICE_COUNT=" + $plist.total)
    foreach ($row in $plist.rows) {
        Write-Output ("  PRICE " + $row.priceId + " | " + $row.price + " | " + $row.effectiveFrom + " -> " + $row.effectiveTo)
    }

    # guard: station has gun -> delete refused
    $g1 = Post '/gas/station/remove' @{ ids=$station.stationId }
    Write-Output ("GUARD_STATION_WITH_GUN code=" + $g1.code + " msg=" + $g1.msg)

    # cleanup: delete newest price (should reopen old), then remaining, gun, station, buyer
    $newest = $plist.rows | Sort-Object { [datetime]$_.effectiveFrom } -Descending | Select-Object -First 1
    $d1 = Post '/gas/price/remove' @{ ids=$newest.priceId }
    Write-Output ("PRICE_DEL_NEWEST code=" + $d1.code + " msg=" + $d1.msg)
    $plist2 = Post '/gas/price/list' @{}
    foreach ($row in $plist2.rows) {
        Write-Output ("  AFTER_DEL " + $row.priceId + " | " + $row.price + " | " + $row.effectiveFrom + " -> " + $row.effectiveTo)
    }
    $ids = ($plist2.rows | ForEach-Object { $_.priceId }) -join ','
    if ($ids) { $d2 = Post '/gas/price/remove' @{ ids=$ids }; Write-Output ("PRICE_DEL_REST code=" + $d2.code) }

    $d3 = Post '/gas/gun/remove' @{ ids=$gun.gunId }
    Write-Output ("GUN_DEL code=" + $d3.code)
    $d4 = Post '/gas/station/remove' @{ ids=$station.stationId }
    Write-Output ("STATION_DEL code=" + $d4.code)
    $d5 = Post '/gas/buyer/remove' @{ ids=$buyer.buyerId }
    Write-Output ("BUYER_DEL code=" + $d5.code)

    $finallist = Post '/gas/buyer/list' @{}
    Write-Output ("FINAL_BUYER_COUNT=" + $finallist.total)

    $pg = Invoke-WebRequest -Uri "$base/gas/buyer" -WebSession $s -UseBasicParsing
    Write-Output ("PAGE_BUYER_STATUS=" + $pg.StatusCode)
}
finally {
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 1
    $l = Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue
    if ($l) { $l | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } }
    Write-Output 'APP_STOPPED'
}

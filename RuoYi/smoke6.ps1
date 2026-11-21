# 阶段6 HTTP 冒烟：员工班次、会员码代下单、司机确认、核销FIFO、跨站/班次变更、强制交班
$ErrorActionPreference = 'Stop'
if (Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue) { throw '端口9090已占用，停止阶段6冒烟' }
$base = 'http://localhost:9090'
$jar = 'ruoyi-admin/target/ruoyi-admin.jar'
$log = 'ruoyi-admin/target/smoke6.log'
$errlog = 'ruoyi-admin/target/smoke6.err.log'
$mysql = 'C:/Program Files/MySQL/MySQL Server 8.3/bin/mysql.exe'
$tag = 'S6' + (Get-Date -Format 'HHmmss')
$fail = 0
function Check($name, $ok) { if ($ok) { Write-Output "PASS $name" } else { $script:fail++; Write-Output "FAIL $name" } }
function RunSql($sql) { & $mysql -uroot -proot ry --default-character-set=utf8mb4 -e $sql | Out-Null }
function Post($uri, $headers, $body = $null) {
    if ($null -eq $body) { return Invoke-RestMethod -Uri "$base$uri" -Method Post -Headers $headers }
    return Invoke-RestMethod -Uri "$base$uri" -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json) -Headers $headers
}

# 两站、两名站点A员工（加气员+站管）、一名站点B加气员
RunSql @"
insert into gas_buyer (buyer_name,short_name,status,create_by,create_time) values ('$tag采购商','$tag','0','smoke6',sysdate());
set @buyer=last_insert_id();
insert into gas_buyer_merchant (buyer_id,channel,merchant_no,status,create_by,create_time) values (@buyer,'LAKALA','MKT-$tag','0','smoke6',sysdate());
insert into sys_dept (parent_id,ancestors,dept_name,order_num,status,del_flag,create_by,create_time) values (100,'0,100','$tag部门A',99,'0','0','smoke6',sysdate()); set @deptA=last_insert_id();
insert into sys_dept (parent_id,ancestors,dept_name,order_num,status,del_flag,create_by,create_time) values (100,'0,100','$tag部门B',99,'0','0','smoke6',sysdate()); set @deptB=last_insert_id();
insert into gas_station (station_code,station_name,buyer_id,dept_id,qr_token,status,create_by,create_time) values ('$tag-A','$tag站点A',@buyer,@deptA,'QR-$tag-A','0','smoke6',sysdate()); set @stationA=last_insert_id();
insert into gas_station (station_code,station_name,buyer_id,dept_id,qr_token,status,create_by,create_time) values ('$tag-B','$tag站点B',@buyer,@deptB,'QR-$tag-B','0','smoke6',sysdate()); set @stationB=last_insert_id();
insert into gas_gun (station_id,gun_no,gas_type,status,create_by,create_time) values (@stationA,1,'CNG','0','smoke6',sysdate()); set @gunA=last_insert_id();
insert into gas_gun (station_id,gun_no,gas_type,status,create_by,create_time) values (@stationB,1,'CNG','0','smoke6',sysdate()); set @gunB=last_insert_id();
insert into gas_gun_price (gun_id,price,effective_from,create_by,create_time) values (@gunA,3.5000,date_sub(sysdate(),interval 1 minute),'smoke6',sysdate());
insert into gas_gun_price (gun_id,price,effective_from,create_by,create_time) values (@gunB,3.5000,date_sub(sysdate(),interval 1 minute),'smoke6',sysdate());
insert into gas_inventory_pool (station_id,buyer_id,gas_type,total_in,adjusted,consumed,reserved,alert_threshold) values (@stationA,@buyer,'CNG',1000,0,0,0,0),(@stationB,@buyer,'CNG',1000,0,0,0,0);
insert into gas_purchase_batch (batch_no,station_id,buyer_id,gas_type,qty,unit_price,amount,purchase_date,pay_status,invoice_status,create_by,create_time) values ('B-$tag-A',@stationA,@buyer,'CNG',1000,3,3000,curdate(),'UNPAID','UNINVOICED','smoke6',sysdate()); set @batchA=last_insert_id();
insert into gas_purchase_batch (batch_no,station_id,buyer_id,gas_type,qty,unit_price,amount,purchase_date,pay_status,invoice_status,create_by,create_time) values ('B-$tag-B',@stationB,@buyer,'CNG',1000,3,3000,curdate(),'UNPAID','UNINVOICED','smoke6',sysdate()); set @batchB=last_insert_id();
insert into gas_purchase_receipt (batch_id,buyer_id,station_id,gas_type,qty,consumed,adjusted,received_at,create_by,create_time) values (@batchA,@buyer,@stationA,'CNG',1000,0,0,sysdate(),'smoke6',sysdate()),(@batchB,@buyer,@stationB,'CNG',1000,0,0,sysdate(),'smoke6',sysdate());
insert into sys_user (login_name,user_name,password,salt,status,del_flag,create_by,create_time,dept_id) values ('$tag-a','$tag加气员A',md5(concat('$tag-a','Gas123456','S6salt')),'S6salt','0','0','smoke6',sysdate(),@deptA); set @userA=last_insert_id();
insert into sys_user_role (user_id,role_id) values (@userA,103);
insert into gas_staff_binding (user_id,openid,station_id,bound_at) values (@userA,'MOCK-$tag-A',@stationA,sysdate());
insert into sys_user (login_name,user_name,password,status,del_flag,create_by,create_time,dept_id) values ('$tag-admin','$tag站管','unused','0','0','smoke6',sysdate(),@deptA); set @userAdmin=last_insert_id();
insert into sys_user_role (user_id,role_id) values (@userAdmin,102);
insert into gas_staff_binding (user_id,openid,station_id,bound_at) values (@userAdmin,'MOCK-$tag-ADMIN',@stationA,sysdate());
insert into sys_user (login_name,user_name,password,status,del_flag,create_by,create_time,dept_id) values ('$tag-b','$tag加气员B','unused','0','0','smoke6',sysdate(),@deptB); set @userB=last_insert_id();
insert into sys_user_role (user_id,role_id) values (@userB,103);
insert into gas_staff_binding (user_id,openid,station_id,bound_at) values (@userB,'MOCK-$tag-B',@stationB,sysdate());
"@

$p = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$jar`"", '--shiro.user.captchaEnabled=false', '--gas.h5.mock-mode=true', '--spring.quartz.auto-startup=false') -RedirectStandardOutput $log -RedirectStandardError $errlog -PassThru -WindowStyle Hidden
Write-Output "PID=$($p.Id) TAG=$tag"
try {
    $up = $false
    for ($i=0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        if ($p.HasExited) { break }
        try { if ((Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200) { $up=$true; break } } catch {}
    }
    if (-not $up) { Write-Output 'APP_NOT_UP'; exit 1 }
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $http = New-Object System.Net.Http.HttpClient($handler)
    function TokenFor($code) {
        $r1 = $http.GetAsync("$base/api/h5/wx/authorize?redirect=/index").Result
        $loc = "$($r1.Headers.Location)" -replace 'code=MOCK', "code=$code"
        if ($loc.StartsWith('/')) { $loc = $base + $loc }
        $r2 = $http.GetAsync($loc).Result
        $loginCode = ("$($r2.Headers.Location)" -split 'h5_code=')[1]
        return (Invoke-RestMethod -Uri "$base/api/h5/wx/exchange" -Method Post -Body @{ code=$loginCode }).data
    }
    function HeadersFor($token) { return @{ Authorization="Bearer $token"; 'X-Requested-With'='XMLHttpRequest' } }
    $member = HeadersFor (TokenFor "$tag-M")
    $staff = HeadersFor (TokenFor "$tag-A")
    $admin = HeadersFor (TokenFor "$tag-ADMIN")
    $staffB = HeadersFor (TokenFor "$tag-B")
    RunSql "update gas_member set phone='13900006666',phone_bound_at=sysdate() where openid='MOCK-$tag-M';"

    $meM = Invoke-RestMethod -Uri "$base/api/h5/c/me" -Headers $member
    $meS = Invoke-RestMethod -Uri "$base/api/h5/c/me" -Headers $staff
    Check 'TWO_IDENTITIES' ($meM.data.role -eq 'MEMBER' -and $meS.data.role -eq 'STAFF')
    $work = Invoke-RestMethod -Uri "$base/api/h5/s/workbench" -Headers $staff
    Check 'WORKBENCH' ($work.code -eq 0 -and $work.data.stationName -eq "$tag站点A")
    $shift = Post '/api/h5/s/shift/start' $staff
    Check 'START_SHIFT' ($shift.code -eq 0 -and $shift.data.activeFlag -eq 1)
    $shiftId = $shift.data.shiftId
    $busy = Post '/api/h5/s/shift/start' $admin
    Check 'SECOND_SHIFT_REJECT' ($busy.code -ne 0 -and $busy.msg -like '*当班*')

    $memberCode = (Invoke-RestMethod -Uri "$base/api/h5/c/member/token" -Headers $member).data.token
    $inspect = Post '/api/h5/s/proxy/inspect' $staff @{ token=$memberCode }
    Check 'PROXY_INSPECT' ($inspect.code -eq 0 -and $inspect.data.shiftId -eq $shiftId -and $inspect.data.guns.Count -eq 1)
    $gunId = $inspect.data.guns[0].gunId
    $proxy = Post '/api/h5/s/proxy/order' $staff @{ token=$memberCode; shiftId=$shiftId; gunId=$gunId; orderAmount='35.00' }
    Check 'PROXY_WAIT_CONFIRM' ($proxy.code -eq 0 -and $proxy.data.status -eq 'WAIT_CONFIRM' -and $proxy.data.source -eq 'PROXY')
    $orderNo = $proxy.data.orderNo
    $reserved0 = & $mysql -uroot -proot ry -N -e "select count(1) from gas_inventory_ledger l join gas_order o on l.order_id=o.order_id where o.order_no='$orderNo' and l.biz_type='RESERVE';" 2>$null
    Check 'NO_RESERVE_BEFORE_CONFIRM' ([int]$reserved0 -eq 0)
    $confirmed = Post "/api/h5/c/order/$orderNo/confirm" $member @{}
    Check 'DRIVER_CONFIRM' ($confirmed.code -eq 0 -and $confirmed.data.status -eq 'WAIT_PAY')
    $pay = Post "/api/h5/c/order/$orderNo/pay" $member
    $payUrl = $pay.data.payUrl
    $payResult = Invoke-WebRequest -Uri "$base/api/pay/mock/cashier/pay" -Method Post -Body @{ outTradeNo=($payUrl -replace '.*outTradeNo=',''); payChannel='WECHAT' } -UseBasicParsing
    $paid = Invoke-RestMethod -Uri "$base/api/h5/c/order/$orderNo" -Headers $member
    Check 'PROXY_PAID' ($payResult.Content -like '*支付成功*' -and $paid.data.status -eq 'PAID')

    $redeemCode = (Invoke-RestMethod -Uri "$base/api/h5/c/order/$orderNo/token" -Headers $member).data.token
    $ri = Post '/api/h5/s/redeem/inspect' $staff @{ token=$redeemCode }
    Check 'REDEEM_INSPECT' ($ri.code -eq 0 -and $ri.data.orderNo -eq $orderNo)
    $redeemed = Post '/api/h5/s/redeem' $staff @{ token=$redeemCode; shiftId=$shiftId; actualVolume='10.00' }
    Check 'REDEEM_COMPLETED' ($redeemed.code -eq 0 -and $redeemed.data.status -eq 'COMPLETED' -and $redeemed.data.completedShiftId -eq $shiftId)
    $repeat = Post '/api/h5/s/redeem' $staff @{ token=$redeemCode; shiftId=$shiftId; actualVolume='10.00' }
    Check 'REDEEM_REPEAT_REJECT' ($repeat.code -ne 0)
    $ended = Post "/api/h5/s/shift/$shiftId/end" $staff
    Check 'SHIFT_SUMMARY' ($ended.code -eq 0 -and $ended.data.proxyCount -eq 1 -and $ended.data.redeemCount -eq 1 -and [decimal]$ended.data.payAmount -eq 35 -and [decimal]$ended.data.cngVolume -eq 10)

    # 另一笔已支付单用于跨站和旧班次校验
    $self = Post '/api/h5/c/order' $member @{ gunId=$gunId; orderAmount='35.00' }
    $selfNo = $self.data.orderNo
    $selfPay = Post "/api/h5/c/order/$selfNo/pay" $member
    Invoke-WebRequest -Uri "$base/api/pay/mock/cashier/pay" -Method Post -Body @{ outTradeNo=($selfPay.data.payUrl -replace '.*outTradeNo=',''); payChannel='WECHAT' } -UseBasicParsing | Out-Null
    $selfCode = (Invoke-RestMethod -Uri "$base/api/h5/c/order/$selfNo/token" -Headers $member).data.token
    $shiftB = Post '/api/h5/s/shift/start' $staffB
    $cross = Post '/api/h5/s/redeem' $staffB @{ token=$selfCode; shiftId=$shiftB.data.shiftId; actualVolume='1.00' }
    Check 'CROSS_STATION_REJECT' ($cross.code -ne 0 -and $cross.msg -like '*跨站*')
    $adminShift = Post '/api/h5/s/shift/start' $admin
    $changed = Post '/api/h5/s/redeem' $staff @{ token=$selfCode; shiftId=$shiftId; actualVolume='1.00' }
    Check 'OLD_SHIFT_REJECT' ($changed.code -ne 0 -and $changed.msg -like '*班次已变更*')

    # 模拟支付成功但预占尚未完成，禁止无预占核销；过期码即使其他条件满足也不可消费
    RunSql "update gas_inventory_pool p join gas_order o on p.station_id=o.station_id and p.gas_type=o.gas_type set p.reserved=greatest(0,p.reserved-o.est_volume) where o.order_no='$selfNo'; delete l from gas_inventory_ledger l join gas_order o on l.order_id=o.order_id where o.order_no='$selfNo' and l.biz_type='RESERVE';"
    $missingReserve = Post '/api/h5/s/redeem' $admin @{ token=$selfCode; shiftId=$adminShift.data.shiftId; actualVolume='1.00' }
    Check 'MISSING_RESERVE_REJECT' ($missingReserve.code -ne 0 -and $missingReserve.msg -like '*预占*')
    RunSql "update gas_dynamic_token set expire_at=date_sub(sysdate(),interval 1 second) where token='$selfCode';"
    $expired = Post '/api/h5/s/redeem' $admin @{ token=$selfCode; shiftId=$adminShift.data.shiftId; actualVolume='1.00' }
    Check 'EXPIRED_TOKEN_REJECT' ($expired.code -ne 0 -and $expired.msg -like '*过期*')

    $emptyReason = Post "/api/h5/s/shift/$($adminShift.data.shiftId)/force-end" $admin @{ reason='' }
    Check 'FORCE_REASON_REQUIRED' ($emptyReason.code -ne 0)
    $blankReason = Post "/api/h5/s/shift/$($adminShift.data.shiftId)/force-end" $admin @{ reason='   ' }
    Check 'FORCE_BLANK_REASON_REJECT' ($blankReason.code -ne 0)
    $objectReason = Post "/api/h5/s/shift/$($adminShift.data.shiftId)/force-end" $admin @{ reason=@{} }
    Check 'FORCE_OBJECT_REASON_REJECT' ($objectReason.code -ne 0)
    $forced = Post "/api/h5/s/shift/$($adminShift.data.shiftId)/force-end" $admin @{ reason='忘记交班，管理员处理' }
    Check 'FORCE_END' ($forced.code -eq 0 -and $forced.data.endReason -eq '忘记交班，管理员处理')

    # 新班次代下单后由司机拒绝
    $newShift = Post '/api/h5/s/shift/start' $staff
    $memberCode2 = (Invoke-RestMethod -Uri "$base/api/h5/c/member/token" -Headers $member).data.token
    $rejectOrder = Post '/api/h5/s/proxy/order' $staff @{ token=$memberCode2; shiftId=$newShift.data.shiftId; gunId=$gunId; orderAmount='20.00' }
    $rejected = Post "/api/h5/c/order/$($rejectOrder.data.orderNo)/reject" $member
    Check 'DRIVER_REJECT' ($rejected.code -eq 0 -and $rejected.data.status -eq 'CLOSED')

    # 未绑定微信通过后台账号密码完成员工绑定，返回的新JWT可实时识别员工身份
    $bindCandidate = HeadersFor (TokenFor "$tag-BIND")
    $bound = Post '/api/h5/staff/bind' $bindCandidate @{ loginName="$tag-a"; password='Gas123456' }
    $boundMe = if ($bound.token) { Invoke-RestMethod -Uri "$base/api/h5/c/me" -Headers (HeadersFor $bound.token) } else { $null }
    Check 'STAFF_BIND_SUCCESS' ($bound.code -eq 0 -and $bound.role -eq 'STAFF' -and $boundMe.data.role -eq 'STAFF' -and $boundMe.data.userId -eq $meS.data.userId)
    Write-Output "RESULT FAIL_COUNT=$fail"
}
finally {
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 500
    }
    $l = Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue
    if ($l) { $l | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } }
    RunSql @"
set @buyer=(select buyer_id from gas_buyer where buyer_name='$tag采购商');
delete t from gas_dynamic_token t left join gas_order o on t.token_type='REDEEM' and t.ref_id=o.order_id left join gas_member m on t.token_type='MEMBER' and t.ref_id=m.member_id where o.buyer_id=@buyer or m.openid='MOCK-$tag-M';
delete l from gas_order_log l join gas_order o on l.order_id=o.order_id where o.buyer_id=@buyer;
delete x from gas_pay_txn x join gas_order o on x.order_id=o.order_id where o.buyer_id=@buyer;
delete l from gas_inventory_ledger l join gas_inventory_pool p on l.pool_id=p.pool_id where p.buyer_id=@buyer;
delete from gas_order where buyer_id=@buyer;
delete s from gas_shift s join gas_station st on s.station_id=st.station_id where st.buyer_id=@buyer;
delete b from gas_staff_binding b join gas_station st on b.station_id=st.station_id where st.buyer_id=@buyer;
delete from sys_user_role where user_id in (select user_id from sys_user where create_by='smoke6');
delete from sys_logininfor where login_name like '$tag-%';
delete from sys_user where create_by='smoke6';
delete r from gas_purchase_receipt r join gas_station s on r.station_id=s.station_id where s.buyer_id=@buyer;
delete b from gas_purchase_batch b join gas_station s on b.station_id=s.station_id where s.buyer_id=@buyer;
delete from gas_inventory_pool where buyer_id=@buyer;
delete p from gas_gun_price p join gas_gun g on p.gun_id=g.gun_id join gas_station s on g.station_id=s.station_id where s.buyer_id=@buyer;
delete g from gas_gun g join gas_station s on g.station_id=s.station_id where s.buyer_id=@buyer;
delete from gas_station where buyer_id=@buyer;
delete from gas_buyer_merchant where buyer_id=@buyer;
delete from sys_dept where dept_name in ('$tag部门A','$tag部门B');
delete from gas_buyer where buyer_id=@buyer;
delete from gas_member where openid in ('MOCK-$tag-M','MOCK-$tag-BIND');
"@
    Write-Output 'CLEANED'
}
exit $fail

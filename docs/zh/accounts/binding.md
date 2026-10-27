# 绑定与切换

## Bukkit/Paper/Folia: 绑定正版

{% stepper %}
{% step %}
### 登录密码账号

使用认证插件登录自己的密码账号.
{% endstep %}

{% step %}
### 申请绑定

输入 `/flp premium`. 如果收到确认提示, 按提示再次输入该命令.
{% endstep %}

{% step %}
### 等待结果并重连

等待最终结果, 然后使用对应正版账号手动重连.
{% endstep %}

{% step %}
### 检查登录

确认下一次登录结果和玩家数据符合预期.
{% endstep %}
{% endstepper %}

{% hint style="info" %}

- 代理模式下, 请求已发送不代表名单修改已完成. 请等待最终反馈.
- `kick-toggle` 决定适用切换后是否踢出玩家; 未被踢出时自行退出并重连.
- 命令本身不会验证下一次连接, 也不会迁移玩家数据. 管理员操作其他玩家时, 按反馈安排玩家重连.
{% endhint %}

## Bukkit/Paper/Folia: 切回密码登录

{% hint style="warning" %}
**操作前先备份认证数据库.** AuthMe 可能删除或重置账号记录, 玩家可能需要重新注册; 不要假设原密码会保留.
{% endhint %}

{% stepper %}
{% step %}
### 登录并检查权限

确认玩家已登录, 且具备执行 `/flp cracked` 的权限.
{% endstep %}

{% step %}
### 切换登录方式

输入 `/flp cracked`, 等待最终结果.
{% endstep %}

{% step %}
### 重连并登录

按提示手动重连, 使用认证插件要求的密码或注册流程登录.
{% endstep %}
{% endstepper %}

{% hint style="info" %}
其他认证插件的清理和密码恢复行为可能不同. 如果插件自动生成过密码, 切换前先确认该插件的恢复方法. 管理员可使用 `/flp cracked <玩家>`, 权限见[命令与权限](commands.md).
{% endhint %}

## Fabric/EasyAuth: 绑定正版

{% stepper %}
{% step %}
### 登录 EasyAuth

登录已有的 EasyAuth 密码账号.
{% endstep %}

{% step %}
### 申请绑定

输入 `/flp premium`. 如果收到确认提示, 按提示再次输入该命令.
{% endstep %}

{% step %}
### 等待结果并重连

等待最终结果, 然后使用对应正版账号手动重连.
{% endstep %}

{% step %}
### 检查登录

验证通过后, 确认 EasyAuth 已完成绑定并登录.
{% endstep %}
{% endstepper %}

{% hint style="info" %}
原密码会保留, 解绑后可以恢复使用. 请求已发送不代表绑定完成. 失败时先检查代理和后端日志, 不要删除原密码账号. 管理员可通过 `/flp premium <玩家>` 授权, 但玩家仍需要在之后以正版身份重连.
{% endhint %}

## Fabric/EasyAuth: 切回密码或注册

{% hint style="warning" %}
**无密码的账号会在确认后开放重新注册.** 先安排安全的注册时机, 避免被其他人抢注.
{% endhint %}

{% stepper %}
{% step %}
### 登录并检查权限

登录账号, 确认具有管理员权限.
{% endstep %}

{% step %}
### 切换登录方式

输入 `/flp cracked`. 如果提示账号没有密码且将开放注册, 在 30 秒内再次输入相同命令确认.
{% endstep %}

{% step %}
### 重连并登录

按提示重连: 原有密码的账号使用原密码; 没有密码的账号重新注册.
{% endstep %}
{% endstepper %}

{% hint style="info" %}
管理员可以指定其他玩家. `/flp delete <玩家>` 只请求代理删除非 premium FLP 记录, 不删除 EasyAuth 账号; 已绑定的本地账号需要先处理. 如果代理请求已发送但本地保存失败, 检查两端日志和账号状态后再重试.
{% endhint %}

## 操作前的共同要求

账号操作应由已登录的持有人或获得授权的管理员执行. 保留认证插件对未登录命令的限制, 不要把 `/flp` 加入未认证放行名单. 涉及切换或恢复时, 备份 FLP 和认证插件数据; UUID 变化可能影响背包和权限, 见 [UUID 与玩家数据](uuid.md).

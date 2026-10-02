# Energetics Power Grid（电力电网）

一个 [Create](https://www.curseforge.com/minecraft/mc-mods/create) 与 [Create: Electro-Energetics（电力学 / CEE）](https://modrinth.com/mod/electroenergetics) 的附属模组，为电力学电网补充照明、加热、励磁与半导体等内容。

## 环境要求

| 项目 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Create | 6.0.10 |
| Create: Electro-Energetics | 1.2.0-130 及以上 |

## 内容一览

### 照明系统（真实热学模拟）

- **灯具固定座（Light Fixture）**：可安装灯泡的电气灯具。灯丝电阻随温度变化（金属正温度系数），通电发热按热容/散热系数积分，**过压或过热持续即烧毁**（可在配置中关闭元件损坏）；
- **LV 白炽灯泡 / 白炽灯泡**：不同额定电压与功率档位，可染色；
- **植物生长灯（Growth Lamp）**：点亮时对朝向位置的作物执行催熟 tick；
- **工厂灯（Factory Light）**：大功率电气顶灯，带投影光照。

### 加热与换向

- **加热线圈（Heating Coil）**：消耗电功率发热，可为 Create 风扇提供鼓风加工（熏制/熔炼类配方）；
- **换向开关（Reversing Switch）**：切换电路极性/通路方向的电气开关。

### 励磁系统

- **励磁定子（Excitation Stator）**：为相邻转子提供励磁场强，磁场强度 `B = I·U·y`（y 为配置系数）。原版定子被并入同一励磁体系（等价固定 100 场强），两套系统对转子自然叠加；
- 励磁强度**连续可调**：转子的有效磁铁数、发电功率、机械应力均随励磁连续变化（无整数台阶）；
- **三相发电机频率下垂**：频率偏移与负载相对稳定基线的偏差量成正比（满量程可配置，默认 100kW 对应 0.08Hz），偏差小于满量程 5% 时频率完全不波动——模拟真实电网的一次调频与缓慢恢复。

### 半导体

- **NPN / PNP 三极管**：三个参数滚轮可调——放大倍数 β、导通电压 V_BE(on)、最大集电极电流 I_C。分段线性模型 + 电力学非线性求解机制（与二极管同款，方向安全），导通/放大/饱和三区齐备；
- 可直接复刻现实器件：例如 **S9013** → β=100、V_BE=0.7V、I_C=0.5A（长电分档：D 档 80 / E 档 100 / F 档 125 / G 档 170）；
- 典型用途：开关电路、报警电路、放大电路。

### 显示连接器集成

- **频率表**：通过显示连接器对外输出当前频率，**精确到小数点后三位**（xx.xxx Hz）；
- **同步钟**：对外输出表盘读数（xx h xx min，360° 相位差 = 12 小时）。

## 配置

服务端配置文件 `energeticspowergrid-server.toml`（或存档内 serverconfig），主要可调项：

| 配置 | 默认 | 说明 |
|---|---|---|
| `componentDamage` | 开 | 元件过压/过热是否烧毁 |
| `frequencyDipMaxHz` | 0.08 | 频率偏移上限（Hz） |
| `frequencyDipFullScaleKilowatt` | 100 | 负载变化达到该千瓦数时频率偏移打满上限 |
| `frequencyDipDecayTicks` | 100 | 负载稳定后频率恢复窗口（刻） |
| `excitationFieldPerStator` | 1000 | 一台定子等效的励磁场强 |
| `excitationStatorFactor` | 0.1 | 励磁公式 B = I·U·y 中的 y |

## 计划中

- 用三相电的电动机
- 自动同步装置（并网自动整步）


## 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。

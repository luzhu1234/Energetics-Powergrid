package com.energeticspowergrid.content.excitation;

/**
 * 转子方块实体的鸭子接口：暴露未取整的连续有效磁铁数。
 * <p>
 * 由 {@code AlternatorRotorBlockEntityMixin} 通过 mixin 的 implements
 * 子句实现到电力学的 {@code AlternatorRotorBlockEntity} 上，供碳刷侧的
 * mixin 读取连续值来计算功率与应力，使发电功率随励磁连续变化而不是
 * 按整块磁铁跳台阶。
 * <p>
 * 注意：本接口必须放在 mixin 包（*.mixin.*）之外——电力学的类被注入
 * implements 后会直接引用它，而 Mixin 禁止外部类引用 mixin 包内的类。
 */
public interface EPGRotorFractional {
    /**
     * 当前未取整的有效磁铁数。
     *
     * @return 连续有效磁铁数；-1 表示尚未计算过（调用方应回退到 int magnets）
     */
    float epg$getFractionalMagnets();

    /**
     * 读取取整后的 int 磁铁数（电力学的 magnets 字段是包级私有，外部包
     * 无法直接访问，必须经由该鸭子方法穿透）。
     *
     * @return 当前 int 磁铁数
     */
    int epg$getMagnets();
}

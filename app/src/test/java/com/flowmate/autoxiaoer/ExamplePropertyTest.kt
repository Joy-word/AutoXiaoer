package com.flowmate.autoxiaoer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * 示例属性测试文件
 *
 * 展示如何使用 Kotest 进行属性测试 (Property-Based Testing)
 * 每个属性测试会自动生成多个随机输入进行验证
 */
class ExamplePropertyTest :
    StringSpec({

        /**
         * Property 1: 字符串反转的往返属性
         * 对任意字符串，反转两次应该得到原字符串
         *
         * **Validates: 往返属性示例**
         */
        "string reverse round-trip should return original" {
            checkAll<String> { str ->
                str.reversed().reversed() shouldBe str
            }
        }

        /**
         * Property 2: 列表大小不变性
         * 对任意列表进行 map 操作后，大小应该保持不变
         *
         * **Validates: 不变性属性示例**
         */
        "list map should preserve size" {
            checkAll(Arb.int(1, 100)) { size ->
                val list = (1..size).toList()
                val mapped = list.map { it * 2 }
                mapped.size shouldBe list.size
            }
        }

        /**
         * Property 3: 排序后的列表应该有序
         * 对任意整数列表排序后，每个元素应该小于等于下一个元素
         *
         * **Validates: 后置条件属性示例**
         */
        "sorted list should be ordered" {
            checkAll(Arb.int(-1000, 1000), Arb.int(-1000, 1000), Arb.int(-1000, 1000)) { a, b, c ->
                val sorted = listOf(a, b, c).sorted()
                sorted[0] shouldBe sorted.min()
                sorted[2] shouldBe sorted.max()
            }
        }

        /**
         * Property 4: 幂等性
         * 对集合去重操作是幂等的，执行多次结果相同
         *
         * **Validates: 幂等性属性示例**
         */
        "distinct operation should be idempotent" {
            checkAll(Arb.int(1, 10), Arb.int(1, 10), Arb.int(1, 10)) { a, b, c ->
                val list = listOf(a, b, c, a, b)
                val once = list.distinct()
                val twice = once.distinct()
                once shouldBe twice
            }
        }

        /**
         * Property 5: 字符串非空检查
         * 非空字符串的长度应该大于 0
         *
         * **Validates: 基本属性示例**
         */
        "non-blank string should have positive length" {
            checkAll(Arb.string(1, 50)) { str ->
                if (str.isNotBlank()) {
                    str.trim().length shouldNotBe 0
                }
            }
        }
    })

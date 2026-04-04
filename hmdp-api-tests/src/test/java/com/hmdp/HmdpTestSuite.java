package com.hmdp;

import com.hmdp.tests.user.UserLoginTest;
import com.hmdp.tests.user.UserSignTest;
import com.hmdp.tests.shop.ShopQueryTest;
import com.hmdp.tests.seckill.SeckillConcurrentTest;
import com.hmdp.tests.blog.BlogQueryTest;
import com.hmdp.tests.follow.FollowTest;
import com.hmdp.tests.voucher.VoucherTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * 测试套件 - 运行所有测试
 */
@Suite
@SelectClasses({
    UserLoginTest.class,
    UserSignTest.class,
    ShopQueryTest.class,
    SeckillConcurrentTest.class,
    BlogQueryTest.class,
    FollowTest.class,
    VoucherTest.class
})
public class HmdpTestSuite {
    // 测试套件类，用于聚合所有测试
}

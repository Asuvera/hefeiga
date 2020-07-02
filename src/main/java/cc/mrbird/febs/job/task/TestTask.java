package cc.mrbird.febs.job.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author MrBird
 */
@Slf4j
@Component
public class TestTask {

    public void test(String params) {
        log.info("我是带参数的test方法，正在被执行，参数为：{}" , params);
    }
    public void test1() {
        log.info("我是不带参数的test1方法，正在被执行");
    }
    public void testZsh(){
        System.out.println("测试组织架构同步定时任务"+new Date());
        log.info("我是带参数的test方法，正在被执行，参数为：{zsh}");
    }
}

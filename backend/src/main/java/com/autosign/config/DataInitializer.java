package com.autosign.config;

import com.autosign.entity.SysUser;
import com.autosign.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private SysUserMapper userMapper;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否存在管理员用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, "admin");
        SysUser admin = userMapper.selectOne(wrapper);

        if (admin == null) {
            // 创建默认管理员
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setPassword(encoder.encode("admin123"));
            user.setNickname("管理员");
            user.setStatus(1);
            userMapper.insert(user);
            System.out.println("默认管理员用户已创建: admin/admin123");
        } else {
            // 确保密码正确
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            if (!encoder.matches("admin123", admin.getPassword())) {
                admin.setPassword(encoder.encode("admin123"));
                userMapper.updateById(admin);
                System.out.println("管理员密码已重置为: admin123");
            }
        }
    }
}

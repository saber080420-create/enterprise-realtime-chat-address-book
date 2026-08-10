package org.itheima.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;
import org.itheima.pojo.Department;
import org.itheima.pojo.Result;
import org.itheima.pojo.User;
import org.itheima.pojo.UserActivity;
import org.itheima.service.DepartmentService;
import org.itheima.service.UserActivityService;
import org.itheima.service.UserService;
import org.itheima.utils.JwtUtil;
import org.itheima.utils.Md5Util;
import org.itheima.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.itheima.service.SystemNoticeService;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    /**
     * 获取客户端IP地址
     * 
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

//    要调用Service的方法前要先注入（声明）Service对象
    @Autowired
    private UserService userService;
    @Autowired
    private UserActivityService userActivityService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private SystemNoticeService systemNoticeService;

    @PostMapping("/register")
    public Result register (
        @Pattern(regexp = "^\\S{5,16}$") String username, 
        @Pattern(regexp = "^\\S{5,16}$") String password,
        Integer departmentId
    ){
        // 查询用户名是否被占用
        User u = userService.findByUserName(username);

        if (u == null){
            // 用户名没被占用，进行注册
            userService.register(username, password, departmentId);
            return Result.success();
        } else {
            // 用户名被占用
            return Result.error("用户名已被占用");
        }
    }
    @PostMapping("/login")
    public Result<String> login (@Pattern(regexp = "^\\S{5,16}$") String username, @Pattern(regexp = "^\\S{5,16}$")String password){
        // 根据用户名查询用户
        User loginuser=userService.findByUserName(username);
        // 判断用户名是否存在
        if (loginuser==null){
            return Result.error("用户名错误");
        }
        
        // 判断用户状态是否正常
        if ("inactive".equals(loginuser.getStatus())) {
            return Result.error("账号已被禁用，请联系系统管理员");
        }

        // 判断密码是否正确，loginuser对象中的password是密文，所以登录时传入的密码要先加密才能比较
        if (Md5Util.getMD5String(password).equals(loginuser.getPassword())){
            // 登录成功
            Map<String,Object> claims=new HashMap<>();
            claims.put("id",loginuser.getId());
            claims.put("username",loginuser.getUsername());
            claims.put("role",loginuser.getRole());
            claims.put("departmentId",loginuser.getDepartmentId()); // 添加部门ID到token中
            String token=JwtUtil.genToken(claims);
            // 单点登录：为该用户只保留最新token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String userKey = "user_token:" + loginuser.getId();
            try {
                String oldToken = operations.get(userKey);
                if (oldToken != null && !oldToken.isEmpty()) {
                    // 失效旧token键
                    stringRedisTemplate.delete(oldToken);
                }
            } catch (Exception ignored) {}
            // 写入新token索引与反向索引（过期时间一致）
            operations.set(token, token, 1, TimeUnit.HOURS);
            operations.set(userKey, token, 1, TimeUnit.HOURS);
            
            // 直接记录用户登录活动
            try {
                // 获取请求对象
                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
                // 获取客户端IP地址
                String ipAddress = getClientIpAddress(request);
                // 获取设备信息（User-Agent）
                String deviceInfo = request.getHeader("User-Agent");
                // 记录登录活动
                userActivityService.recordLogin(loginuser.getId(), ipAddress, deviceInfo);
            } catch (Exception e) {
                // 记录登录活动失败不影响登录流程
                System.err.println("记录登录活动失败: " + e.getMessage());
            }
            
            return Result.success(token);
        }
        return Result.error("密码错误");
    }
    @GetMapping("/userInfo")
    public Result<Map<String, Object>> userInfo(){
        // 根据用户名（已登录）查询用户
        Map<String,Object> map = ThreadLocalUtil.get();
        String username = (String) map.get("username");
        Integer userId = (Integer) map.get("id");
        
        // 获取用户基本信息
        User user = userService.findByUserName(username);
        
        // 获取用户最近登录记录
        UserActivity latestActivity = userActivityService.findLatestByUserId(userId);
        
        // 创建返回结果Map
        Map<String, Object> resultMap = new HashMap<>();
        // 复制用户基本信息
        resultMap.put("id", user.getId());
        resultMap.put("username", user.getUsername());
        resultMap.put("nickname", user.getNickname());
        resultMap.put("realname", user.getRealname());
        resultMap.put("email", user.getEmail());
        resultMap.put("userPic", user.getUserPic());
        resultMap.put("departmentId", user.getDepartmentId());
        
        // 添加部门名称
        if (user.getDepartmentId() != null) {
            Department department = departmentService.findById(user.getDepartmentId());
            if (department != null) {
                resultMap.put("department", department.getDepartmentName());
            }
        }
        
        resultMap.put("position", user.getPosition());
        resultMap.put("employeeNumber", user.getEmployeeNumber());
        resultMap.put("phone", user.getPhone());
        resultMap.put("role", user.getRole());
        
        // 添加注册日期（用户创建时间）
        resultMap.put("registerDate", user.getCreateTime());
        
        // 添加最近登录时间
        if (latestActivity != null) {
            resultMap.put("lastLogin", latestActivity.getLoginTime());
        }
        
        return Result.success(resultMap);
    }
    @PutMapping("/update")
    public Result update(@RequestBody @Validated User user){
        // 从ThreadLocal获取当前登录用户ID
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        // 设置正确的用户ID（忽略请求中可能提供的ID）
        user.setId(userId);
        
        userService.update(user);
        return Result.success();
    }
    @PatchMapping("/updateAvatar")
    public Result updateAvatar (@RequestParam String avatarUrl){
        // 放宽校验：支持 http/https 绝对地址与以 / 开头的相对地址
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            return Result.error("头像URL不能为空");
        }
        String normalized = avatarUrl.replace('\\','/').trim();
        if (!normalized.startsWith("http") && !normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        userService.updateAvatar(normalized);
        return Result.success();
    }
    
    @PutMapping("/update/realname")
    public Result updateRealname(@RequestParam @Pattern(regexp = "^\\S{1,20}$") String realname){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        userService.updateRealname(realname);
        return Result.success();
    }
    
    @PutMapping("/update/nickname")
    public Result updateNickname(@RequestParam @Pattern(regexp = "^\\S{1,10}$") String nickname){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        userService.updateNickname(nickname);
        return Result.success();
    }
    
    @PutMapping("/update/email")
    public Result updateEmail(@RequestParam @Email String email){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        userService.updateEmail(email);
        return Result.success();
    }
    
    @PutMapping("/update/phone")
    public Result updatePhone(@RequestParam @Pattern(regexp = "^1[3-9]\\d{9}$") String phone){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        
        userService.updatePhone(phone);
        return Result.success();
    }
    @PatchMapping("/updatePwd")
    public Result updatePwd(@RequestBody Map<String,String> params,@RequestHeader("Authorization") String token){
        //校验参数
        String oldPwd = params.get("old_pwd");
        String newPwd = params.get("new_pwd");
        String rePwd = params.get("re_pwd");

        if (!StringUtils.hasLength(oldPwd)||!StringUtils.hasLength(newPwd)||!StringUtils.hasLength(rePwd)) {
            return Result.error("缺少必要的参数");
        }

        //原密码是否正确
        //调用userService根据用户名拿到原密码,再和old_pwd比对
        Map<String,Object> map = ThreadLocalUtil.get();
        String username = (String) map.get("username");
        User loginUser = userService.findByUserName(username);
        if (!loginUser.getPassword().equals(Md5Util.getMD5String(oldPwd))){
            return Result.error("原密码填写不正确");
        }

        //校验newPwd和rePwd是否一样
        if (!rePwd.equals(newPwd)){
            return Result.error("两次填写的新密码不一样");
        }
        //调用service更新密码
        userService.updatePwd(newPwd);
        //删除redis中对应的token
        ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
        operations.getOperations().delete(token);
        return Result.success();
    }
    
    // 删除重复的方法，因为UserActivityController已经有相同功能的方法
    // /**
    //  * 记录用户登录活动
    //  */
    // @PostMapping("/activity/login")
    // public Result recordLoginActivity() {
    //     Map<String,Object> map = ThreadLocalUtil.get();
    //     Integer userId = (Integer) map.get("id");
    //     
    //     userActivityService.recordLoginActivity(userId);
    //     return Result.success();
    // }
    
    // 删除重复的方法，因为UserActivityController已经有相同功能的方法
    // /**
    //  * 记录用户登出活动
    //  */
    // @PostMapping("/activity/logout")
    // public Result recordLogoutActivity() {
    //     Map<String,Object> map = ThreadLocalUtil.get();
    //     Integer userId = (Integer) map.get("id");
    //     
    //     userActivityService.recordLogoutActivity(userId);
    //     return Result.success();
    // }
    
    /**
     * 更新用户角色
     * 
     * @param userId 用户ID
     * @param role 角色
     * @return 更新结果
     */
    @PutMapping("/role/{userId}")
    public Result updateUserRole(@PathVariable Integer userId, @RequestParam String role) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer currentUserId = (Integer) claims.get("id");
            String currentUserRole = (String) claims.get("role");
            
            // 检查权限：只有系统管理员可以更改用户角色
            if (!"system_admin".equals(currentUserRole)) {
                return Result.error("只有系统管理员可以更改用户角色");
            }
            
            // 不能更改自己的角色
            if (currentUserId.equals(userId)) {
                return Result.error("不能更改自己的角色");
            }
            
            // 若角色未变化，直接返回成功（避免重复通知）
            User targetBefore = userService.findById(userId);
            if (targetBefore == null) {
                return Result.error("用户不存在");
            }
            if (role != null && role.equals(targetBefore.getRole())) {
                return Result.success();
            }

            // 业务约束：设置为部门管理员时，必须已分配部门，且该部门仅能有1名部门管理员
            if ("department_admin".equals(role)) {
                Integer deptId = targetBefore.getDepartmentId();
                if (deptId == null) {
                    return Result.error("该员工未分配部门，无法设为部门管理员");
                }
                List<User> deptUsers = userService.findByDepartmentId(deptId);
                if (deptUsers != null) {
                    for (User u : deptUsers) {
                        if (u != null && u.getId() != null && !u.getId().equals(userId)
                                && "department_admin".equals(u.getRole())) {
                            return Result.error("该部门已有管理员");
                        }
                    }
                }
            }

            // 更新用户角色
            userService.updateRole(userId, role);
            // 发送系统通知：授予/收回管理员
            String roleName = "普通员工";
            if ("system_admin".equals(role)) roleName = "系统管理员";
            else if ("department_admin".equals(role)) roleName = "部门管理员";
            String content;
            if ("普通员工".equals(roleName)) {
                content = "系统管理员已将你设置为普通员工";
            } else {
                content = "系统管理员已将你设为" + roleName;
            }
            systemNoticeService.sendNotice(userId, currentUserId, "role_changed", "角色变更", content);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 获取所有用户列表（仅系统管理员可用）
     * 
     * @return 用户列表
     */
    @GetMapping("/list")
    public Result<List<User>> getUserList() {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            String currentUserRole = (String) claims.get("role");
            
            // 检查权限：只有系统管理员可以获取所有用户列表
            if (!"system_admin".equals(currentUserRole)) {
                return Result.error("只有系统管理员可以获取所有用户列表");
            }
            
            // 获取所有用户列表
            List<User> users = userService.findAll();
            return Result.success(users);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 更新用户状态（启用/禁用）
     * 
     * @param userId 用户ID
     * @param status 状态（active, inactive）
     * @return 更新结果
     */
    @PutMapping("/status/{userId}")
    public Result updateUserStatus(@PathVariable Integer userId, @RequestParam String status) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer currentUserId = (Integer) claims.get("id");
            String currentUserRole = (String) claims.get("role");
            
            // 检查权限：只有系统管理员可以更改用户状态
            if (!"system_admin".equals(currentUserRole)) {
                return Result.error("只有系统管理员可以更改用户状态");
            }
            
            // 不能更改自己的状态
            if (currentUserId.equals(userId)) {
                return Result.error("不能更改自己的状态");
            }
            
            // 更新用户状态
            userService.updateStatus(userId, status);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 更新用户职位
     * 
     * @param userId 用户ID
     * @param position 职位
     * @return 更新结果
     */
    @PutMapping("/position/{userId}")
    public Result updateUserPosition(@PathVariable Integer userId, @RequestParam String position) {
        try {
            System.out.println("收到更新职位请求 - 用户ID: " + userId + ", 职位: " + position);
            
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer currentUserId = (Integer) claims.get("id");
            String currentUserRole = (String) claims.get("role");
            
            System.out.println("当前用户ID: " + currentUserId + ", 角色: " + currentUserRole);
            
            // 检查权限：只有系统管理员可以更改用户职位
            if (!"system_admin".equals(currentUserRole)) {
                System.out.println("权限不足: 只有系统管理员可以更改用户职位");
                return Result.error("只有系统管理员可以更改用户职位");
            }
            
            // 不能更改自己的职位
            if (currentUserId.equals(userId)) {
                System.out.println("不能更改自己的职位");
                return Result.error("不能更改自己的职位");
            }
            
            // 若职位未变化，直接返回成功（避免重复通知）
            User before = userService.findById(userId);
            if (before == null) {
                return Result.error("用户不存在");
            }
            String oldPos = before.getPosition() == null ? "" : before.getPosition();
            String newPos = position == null ? "" : position;
            if (oldPos.equals(newPos)) {
                return Result.success();
            }

            // 更新用户职位
            System.out.println("开始更新用户职位");
            userService.updatePosition(userId, position);
            System.out.println("用户职位更新成功");
            // 系统通知：职位变更
            try {
                systemNoticeService.sendNotice(userId, currentUserId, "position_changed", "职位变更",
                        "系统管理员已将你的职位调整为 " + position);
            } catch (Exception ignore) {}
            return Result.success();
        } catch (RuntimeException e) {
            System.out.println("更新用户职位异常: " + e.getMessage());
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 删除用户
     * 
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{userId}")
    public Result deleteUser(@PathVariable Integer userId) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer currentUserId = (Integer) claims.get("id");
            String currentUserRole = (String) claims.get("role");
            
            // 检查权限：只有系统管理员可以删除用户
            if (!"system_admin".equals(currentUserRole)) {
                return Result.error("只有系统管理员可以删除用户");
            }
            
            // 不能删除自己
            if (currentUserId.equals(userId)) {
                return Result.error("不能删除自己的账户");
            }
            
            // 删除用户
            userService.deleteUser(userId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 更新用户部门
     * 
     * @param userId 用户ID
     * @param departmentId 部门ID
     * @return 更新结果
     */
    @PutMapping("/department/{userId}")
    public Result updateUserDepartment(@PathVariable Integer userId, @RequestParam Integer departmentId) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer currentUserId = (Integer) claims.get("id");
            String currentUserRole = (String) claims.get("role");
            
            // 检查权限：只有系统管理员可以更改用户部门
            if (!"system_admin".equals(currentUserRole)) {
                return Result.error("只有系统管理员可以更改用户部门");
            }
            
            // 不能更改自己的部门
            if (currentUserId.equals(userId)) {
                return Result.error("不能更改自己的部门");
            }
            
            // 检查部门是否存在
            if (departmentService.findById(departmentId) == null) {
                return Result.error("部门不存在");
            }
            
            // 业务约束：同一员工只能在一个部门；允许系统管理员直接更换部门（无需先移除）
            User target = userService.findById(userId);
            if (target == null) {
                return Result.error("用户不存在");
            }
            Integer currentDeptId = target.getDepartmentId();
            // 若未变化，直接返回成功（避免重复通知）
            if (currentDeptId != null && currentDeptId.equals(departmentId)) {
                return Result.success();
            }
            // 放宽限制：直接允许更换部门（仅系统管理员可调用此接口）

            // 若该用户当前角色是部门管理员，且目标部门已有管理员，拒绝
            if ("department_admin".equals(target.getRole())) {
                List<User> deptUsers = userService.findByDepartmentId(departmentId);
                if (deptUsers != null) {
                    for (User u : deptUsers) {
                        if (u != null && u.getId() != null && !u.getId().equals(userId)
                                && "department_admin".equals(u.getRole())) {
                            return Result.error("该部门已有管理员");
                        }
                    }
                }
            }

            // 更新用户部门
            userService.updateDepartment(userId, departmentId);
            // 通知：部门变更
            String deptName = departmentService.findById(departmentId).getDepartmentName();
            systemNoticeService.sendNotice(userId, currentUserId, "department_changed", "部门变更",
                    "系统管理员已将你调整至" + deptName + "部门");
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 部门管理员将成员移出本部门（将其department_id置为null）
     */
    @PostMapping("/department/remove/{userId}")
    public Result<Boolean> removeUserFromDepartment(@PathVariable Integer userId) {
        try {
            Map<String, Object> claims = ThreadLocalUtil.get();
            Integer operatorId = (Integer) claims.get("id");
            String operatorRole = (String) claims.get("role");
            Integer operatorDeptId = (Integer) claims.get("departmentId");

            User target = userService.findById(userId);
            if (target == null) {
                return Result.error("用户不存在");
            }
            Integer targetDeptId = target.getDepartmentId();
            if (targetDeptId == null) {
                return Result.error("该用户不属于任何部门");
            }

            // 权限：系统管理员可移除任意用户；部门管理员仅可移除本部门普通员工
            if (!"system_admin".equals(operatorRole)) {
                if (!"department_admin".equals(operatorRole)) {
                    return Result.error("权限不足");
                }
                if (operatorDeptId == null || !operatorDeptId.equals(targetDeptId)) {
                    return Result.error("只能移除本部门成员");
                }
                if (!"employee".equals(target.getRole())) {
                    return Result.error("只能移除本部门普通员工");
                }
            }

            Department dept = departmentService.findById(targetDeptId);
            String deptName = dept == null ? "" : dept.getDepartmentName();

            // 置空部门
            userService.updateDepartment(userId, null);

            // 通知
            String title = "部门变更";
            String content;
            if ("system_admin".equals(operatorRole)) {
                content = "系统管理员已将你移出" + deptName + "部门";
            } else {
                content = deptName + "部门管理员已将你移出" + deptName + "部门";
            }
            systemNoticeService.sendNotice(userId, operatorId, "department_removed", title, content);

            return Result.success(true);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 获取部门用户列表
     * 
     * @param departmentId 部门ID
     * @return 用户列表
     */
    @GetMapping("/byDepartment/{departmentId}")
    public Result<List<User>> getUsersByDepartment(@PathVariable Integer departmentId) {
        try {
            // 获取当前登录用户信息
            Map<String, Object> claims = ThreadLocalUtil.get();
            String currentUserRole = (String) claims.get("role");
            Integer currentUserId = (Integer) claims.get("id");
            // 以数据库中最新部门为准，避免 token 内部门ID过期
            Integer currentUserDepartmentId = null;
            try {
                User me = userService.findById(currentUserId);
                if (me != null) currentUserDepartmentId = me.getDepartmentId();
            } catch (Exception ignore) {}
            
            // 详细的调试日志
            System.out.println("===== 部门用户列表权限检查 =====");
            System.out.println("请求部门ID: " + departmentId);
            System.out.println("当前用户ID: " + currentUserId);
            System.out.println("当前用户角色: " + currentUserRole);
            System.out.println("当前用户部门ID: " + currentUserDepartmentId);
            
            // 权限检查：
            // - 系统管理员：可查看任意部门
            // - 部门管理员：可查看自己部门
            // - 普通用户：可查看自己部门
            boolean isSystemAdmin = "system_admin".equals(currentUserRole);
            boolean isDepartmentAdmin = "department_admin".equals(currentUserRole);
            boolean sameDept = currentUserDepartmentId != null && currentUserDepartmentId.equals(departmentId);
            
            System.out.println("是否系统管理员: " + isSystemAdmin);
            System.out.println("是否部门管理员: " + isDepartmentAdmin);
            System.out.println("是否同部门: " + sameDept);
            
            if (!(isSystemAdmin || (isDepartmentAdmin && sameDept) || sameDept)) {
                String errorMsg = String.format("权限不足，您只能查看自己所在部门的成员 [当前角色:%s, 当前部门:%s, 请求部门:%d]", 
                    currentUserRole, String.valueOf(currentUserDepartmentId), departmentId);
                System.out.println("权限检查失败: " + errorMsg);
                return Result.error(errorMsg);
            }
            
            System.out.println("权限检查通过");
            
            // 检查部门是否存在
            if (departmentService.findById(departmentId) == null) {
                return Result.error("部门不存在");
            }
            
            // 获取部门用户列表
            List<User> users = userService.findByDepartmentId(departmentId);
            System.out.println("获取到用户数量: " + (users != null ? users.size() : 0));
            return Result.success(users);
        } catch (RuntimeException e) {
            System.err.println("获取部门用户列表异常: " + e.getMessage());
            return Result.error(e.getMessage());
        }
    }
    
    /**
     * 按关键词搜索用户（用于添加外部联系人）
     *
     * - 系统管理员：可搜索全量用户，返回完整用户信息
     * - 非系统管理员：只返回“本部门以外”的用户，且仅返回最小必要信息以保护隐私
     *
     * @param keyword 关键词（支持用户名/真实姓名/昵称/电话/邮箱模糊匹配）
     * @param limit 返回数量上限，默认20，最大50
     * @return 用户搜索结果
     */
    @GetMapping("/search")
    public Result<List<?>> searchUsers(@RequestParam String keyword,
                                       @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            if (!StringUtils.hasText(keyword)) {
                return Result.error("关键词不能为空");
            }
            keyword = keyword.trim();
            if (limit == null) limit = 20;
            if (limit < 1) limit = 1;
            if (limit > 50) limit = 50;

            Map<String, Object> claims = ThreadLocalUtil.get();
            String role = (String) claims.get("role");
            Integer currentUserId = (Integer) claims.get("id");
            Integer currentDeptId = (Integer) claims.get("departmentId");

            List<User> users = userService.searchUsers(keyword, limit);

            if ("system_admin".equals(role)) {
                // 系统管理员直接返回完整信息
                return Result.success(users);
            }

            // 非系统管理员：仅返回本部门以外的用户，且以最小信息返回（含部门与职位用于预览）
            List<Map<String, Object>> result = new ArrayList<>();
            for (User u : users) {
                if (u.getId() != null && u.getId().equals(currentUserId)) {
                    continue; // 排除自己
                }
                Integer deptId = u.getDepartmentId();
                if (deptId != null && currentDeptId != null && deptId.equals(currentDeptId)) {
                    continue; // 非系统管理员，不返回同部门
                }
                Map<String, Object> item = new HashMap<>();
                item.put("id", u.getId());
                item.put("username", u.getUsername());
                item.put("realname", u.getRealname());
                item.put("nickname", u.getNickname());
                item.put("userPic", u.getUserPic());
                item.put("departmentId", deptId);
                // 新增：职位字段用于前端预览
                item.put("position", u.getPosition());
                if (deptId != null) {
                    Department d = departmentService.findById(deptId);
                    if (d != null) {
                        item.put("department", d.getDepartmentName());
                    }
                }
                result.add(item);
                if (result.size() >= limit) break; // 再次确保上限
            }
            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}

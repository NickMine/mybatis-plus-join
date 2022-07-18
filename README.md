# 工程简介

mybatis-plus的连表的增强，提供lambda和字符串两种方式的wrapper编写sql，项目版本现阶段已适配3.5.2的最新版本的mybatis-plus

| mybatis-plus版本 | join版本 | 备注      |
| -------------- | ------ | ------- |
| 3.5.2          | 3.5.2  | 已适配(最新) |
| 3.5.1          | 3.5.1  | 已适配     |
| 3.4.2          | 3.4.2  | 已适配     |

# 使用

- maven

**Step 1.** Add the JitPack repository to your build file

```markup
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
```

**Step 2.** Add the dependency

```markup
    <dependency>
        <groupId>com.github.NickMine</groupId>
        <artifactId>mybatis-plus-join</artifactId>
        <version>r.3.5.1</version>
    </dependency>
```

- gradle

**Step 1.** Add the JitPack repository to your build file

Add it in your root build.gradle at the end of repositories:

```css
    allprojects {
        repositories {
            ...            maven { url 'https://jitpack.io' }
        }
    }
```

**Step 2.** Add the dependency

```css
    dependencies {
            implementation 'com.github.NickMine:mybatis-plus-join:r.3.5.1'
    }
```



### 代码用例

```java
//设置连表信息,student表，userClass用户关联班级表，classInfo班级信息表
// StudentVo查询列信息Vo返回类
LambdaJoinWrapper<Student> lambdaJoinWrapper = 
    new LambdaJoinWrapper<>(Student.class, StudentVo.class)  
        .leftJoin(UserClass.class,  
                Student::getStudentId,  
                UserClass::getStudentId  
        )  
        .joinTo(UserClass.class)  
        .leftJoin(ClassInfo.class,  
                UserClass::getClassInfoId,  
                ClassInfo::getClassInfoId  
        )  
        .main(Student.class);
//设置条件  
lambdaJoinWrapper
        .main(Student.class)
        // 学生名为张三 
        .eq(Student::getName, "张三")  
        //班级是软件三班
        .joinTo(ClassInfo.class)
        .like(ClassInfo::getClassName, "软件三班");  

//学生岗位数组  
List<StudentVo> studentVoList = studentMapper.findVoList(lambdaJoinWrapper);



```

mapper层(studentMapper)：

```java
@Select(JoinLambdaUtil.SELECT_TEMPLATE)  
List<StudentVo> findVoList(@Param("ew") Wrapper<?> wrapper);
```

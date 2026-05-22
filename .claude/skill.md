## 技能描述

本技能要求在执行任何代码编写、代码重构或代码审查任务时，必须为代码添加极其详细、清晰的中文注释。目标是让任何阅读代码的人（包括初学者）都能毫不费力地理解代码的逻辑、意图和实现细节。同时逐渐完善项目构建教程.md。

## 核心原则

1. **逐行/逐块解释**：对于复杂的逻辑，必须逐行或按最小逻辑块进行注释。
2. **解释“为什么”而非“是什么”**：代码本身展示了“是什么”，注释应侧重于解释“为什么要这样写”以及背后的业务逻辑或算法思路。
3. **包含参数与返回值说明**：所有函数/方法必须包含完整的文档字符串（Docstring），说明参数类型、含义、返回值以及可能抛出的异常。
4. **通俗易懂**：注释语言应简洁明了，避免晦涩难懂的术语，必要时使用比喻。

## 注释规范

### 1. 函数/类级别注释 (Docstring)

在定义任何函数或类时，必须使用文档字符串格式，包含以下内容：

- **功能简述**：一句话概括该函数/类的作用。
- **参数说明 (Args/Params)**：列出每个参数的名称、数据类型和具体含义。
- **返回值说明 (Returns)**：说明返回的数据类型和代表的意义。
- **异常说明 (Raises)**：(可选) 说明可能抛出的异常类型及触发条件。

### 2. 行内注释 (Inline Comments)

- 在关键逻辑行上方或行尾添加注释。
- 解释复杂的算法步骤、正则表达式含义、位运算逻辑等。
- 解释特定的业务规则或“魔法数字”的来源。

### 3. 区块注释 (Block Comments)

- 用于分隔代码的不同逻辑段落（如：数据预处理 -> 模型训练 -> 结果评估）。
- 解释一段代码的整体目的。

## 示例

### Python 示例

```python
def calculate_discount(price, user_type):
    """
    根据用户类型计算商品的折后价格。

    参数:
        price (float): 商品的原始价格，必须大于0。
        user_type (str): 用户类型，支持 'vip', 'new', 'regular'。

    返回值:
        float: 计算后的折后价格，保留两位小数。

    异常:
        ValueError: 当价格小于等于0或用户类型无效时抛出。
    """
    # 1. 基础数据校验：确保输入的价格是合法的正数
    if price <= 0:
        raise ValueError("商品价格必须大于0")

    # 2. 定义不同用户类型对应的折扣率字典
    # key: 用户类型, value: 折扣率 (例如 0.8 代表 8折)
    discount_rates = {
        'vip': 0.8,      # VIP用户享受8折
        'new': 0.9,      # 新用户享受9折
        'regular': 1.0   # 普通用户无折扣
    }

    # 3. 获取当前用户的折扣率，如果用户类型不存在，默认为无折扣(1.0)
    # 使用 .get() 方法可以安全地处理不存在的键，避免程序崩溃
    rate = discount_rates.get(user_type, 1.0)

    # 4. 计算折后价格：原价 * 折扣率
    final_price = price * rate

    # 5. 返回结果，并使用 round() 函数保留两位小数，符合货币显示规范
    return round(final_price, 2)
```

### JavaScript/TypeScript 示例

```javascript
/**
 * 过滤数组中的无效数据并格式化
 * @param {Array} rawData - 包含原始数据的数组
 * @returns {Array} 返回格式化后的有效数据数组
 */
function processUserData(rawData) {
  // 使用 filter 方法筛选出 age 属性存在且大于 18 的用户
  const validUsers = rawData.filter((user) => {
    // 确保 user 对象存在，且 age 是有效数字
    return user && typeof user.age === "number" && user.age >= 18;
  });

  // 使用 map 方法将筛选后的数据转换为我们需要的格式
  const formattedData = validUsers.map((user) => {
    return {
      id: user.id, // 保留用户ID
      displayName: user.name.toUpperCase(), // 将用户名转换为大写作为显示名
      isAdult: true, // 标记为成年人
    };
  });

  return formattedData;
}
```

## 执行指令

**从现在开始，无论用户要求编写什么语言或类型的代码，请务必严格遵守上述注释规范，输出带有详细中文注释的高质量代码和及时的更新本文件内的项目构建教程。**

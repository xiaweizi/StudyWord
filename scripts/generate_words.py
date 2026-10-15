#!/usr/bin/env python3
"""
generate_words.py

为 character_sets.json 的前 N 个常用字生成词组与例句数据,并就地写回文件。

数据来源:内置 curated 字典(覆盖 100 个最常见字,每个字 2~3 个词组 + 1 个例句)。

使用方式:
    python scripts/generate_words.py            # 默认覆盖前 100 个 easy 字
    python scripts/generate_words.py --limit 50 # 自定义覆盖范围
    python scripts/generate_words.py --dry-run  # 仅打印,不写文件
"""

import argparse
import json
import sys
from pathlib import Path

# 强制 UTF-8 输出,Windows 默认 GBK 会让中文乱码
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

# 默认相对路径:脚本放在 scripts/ 下,字库放在 app/src/main/assets/ 下
DEFAULT_JSON = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "character_sets.json"

# 字典格式:{ 字: { "words": [(词组, 拼音)], "examples": [(例句, 拼音)] } }
# 拼音用空格分隔每个字,方便 TTS 逐字朗读。
ENTRIES: dict[str, dict] = {
    "一": {
        "words": [("一起", "yī qǐ"), ("一会儿", "yī huì er"), ("一只", "yī zhī")],
        "examples": [("我们一起去公园。", "wǒ men yī qǐ qù gōng yuán")],
    },
    "二": {
        "words": [("二月", "èr yuè"), ("第二", "dì èr"), ("二十", "èr shí")],
        "examples": [("二月份是我的生日。", "èr yuè fèn shì wǒ de shēng rì")],
    },
    "三": {
        "words": [("三天", "sān tiān"), ("三月", "sān yuè"), ("三角形", "sān jiǎo xíng")],
        "examples": [("再过三天就是周末了。", "zài guò sān tiān jiù shì zhōu mò le")],
    },
    "四": {
        "words": [("四季", "sì jì"), ("四个", "sì ge"), ("四方", "sì fāng")],
        "examples": [("一年有四个季节。", "yì nián yǒu sì ge jì jié")],
    },
    "五": {
        "words": [("五个", "wǔ ge"), ("五彩", "wǔ cǎi"), ("五只", "wǔ zhī")],
        "examples": [("我有五个气球。", "wǒ yǒu wǔ ge qì qiú")],
    },
    "六": {
        "words": [("六个", "liù ge"), ("六月", "liù yuè"), ("六一", "liù yī")],
        "examples": [("桌上摆了六个苹果。", "zhuō shàng bǎi le liù ge píng guǒ")],
    },
    "七": {
        "words": [("七个", "qī ge"), ("七彩", "qī cǎi"), ("七天", "qī tiān")],
        "examples": [("彩虹有七种颜色。", "cǎi hóng yǒu qī zhǒng yán sè")],
    },
    "八": {
        "words": [("八个", "bā ge"), ("八月", "bā yuè"), ("八方", "bā fāng")],
        "examples": [("池塘里有八只小鸭子。", "chí táng lǐ yǒu bā zhī xiǎo yā zi")],
    },
    "九": {
        "words": [("九个", "jiǔ ge"), ("九月", "jiǔ yuè"), ("九只", "jiǔ zhī")],
        "examples": [("九月开学啦。", "jiǔ yuè kāi xué la")],
    },
    "十": {
        "words": [("十个", "shí ge"), ("十月", "shí yuè"), ("十岁", "shí suì")],
        "examples": [("我今年十岁了。", "wǒ jīn nián shí suì le")],
    },
    "天": {
        "words": [("天空", "tiān kōng"), ("今天", "jīn tiān"), ("天气", "tiān qì")],
        "examples": [("今天的天气真好。", "jīn tiān de tiān qì zhēn hǎo")],
    },
    "地": {
        "words": [("土地", "tǔ dì"), ("地球", "dì qiú"), ("地上", "dì shàng")],
        "examples": [("地上有一只小蚂蚁。", "dì shàng yǒu yī zhī xiǎo mǎ yǐ")],
    },
    "人": {
        "words": [("大人", "dà ren"), ("人们", "rén men"), ("小朋友", "xiǎo péng yǒu")],
        "examples": [("公园里有许多人。", "gōng yuán lǐ yǒu xǔ duō rén")],
    },
    "口": {
        "words": [("口袋", "kǒu dai"), ("口水", "kǒu shuǐ"), ("口渴", "kǒu kě")],
        "examples": [("我口渴了想喝水。", "wǒ kǒu kě le xiǎng hē shuǐ")],
    },
    "手": {
        "words": [("小手", "xiǎo shǒu"), ("手指", "shǒu zhǐ"), ("拍手", "pāi shǒu")],
        "examples": [("大家拍手表扬他。", "dà jiā pāi shǒu biǎo yáng tā")],
    },
    "足": {
        "words": [("足球", "zú qiú"), ("足够", "zú gòu"), ("足迹", "zú jì")],
        "examples": [("我喜欢踢足球。", "wǒ xǐ huan tī zú qiú")],
    },
    "日": {
        "words": [("日子", "rì zi"), ("生日", "shēng rì"), ("日记", "rì jì")],
        "examples": [("今天是我的生日。", "jīn tiān shì wǒ de shēng rì")],
    },
    "月": {
        "words": [("月亮", "yuè liang"), ("月亮", "yuè liang"), ("月份", "yuè fèn")],
        "examples": [("月亮圆圆的像月饼。", "yuè liang yuán yuán de xiàng yuè bǐng")],
    },
    "山": {
        "words": [("高山", "gāo shān"), ("上山", "shàng shān"), ("山水", "shān shuǐ")],
        "examples": [("我们一起去爬高山。", "wǒ men yī qǐ qù pá gāo shān")],
    },
    "水": {
        "words": [("喝水", "hē shuǐ"), ("水果", "shuǐ guǒ"), ("河水", "hé shuǐ")],
        "examples": [("小朋友要多喝水。", "xiǎo péng yǒu yào duō hē shuǐ")],
    },
    "火": {
        "words": [("火车", "huǒ chē"), ("火箭", "huǒ jiàn"), ("火星", "huǒ xīng")],
        "examples": [("火车在铁轨上跑。", "huǒ chē zài tiě guǐ shàng pǎo")],
    },
    "木": {
        "words": [("树木", "shù mù"), ("木头", "mù tou"), ("木马", "mù mǎ")],
        "examples": [("森林里有许多树木。", "sēn lín lǐ yǒu xǔ duō shù mù")],
    },
    "田": {
        "words": [("田地", "tián dì"), ("稻田", "dào tián"), ("水田", "shuǐ tián")],
        "examples": [("稻田里稻子绿绿的。", "dào tián lǐ dào zi lǜ lǜ de")],
    },
    "爸": {
        "words": [("爸爸", "bà ba"), ("爸妈", "bà mā")],
        "examples": [("爸爸带我去公园。", "bà ba dài wǒ qù gōng yuán")],
    },
    "妈": {
        "words": [("妈妈", "mā ma"), ("爸妈", "bà mā")],
        "examples": [("妈妈给我讲故事。", "mā ma gěi wǒ jiǎng gù shi")],
    },
    "爷": {
        "words": [("爷爷", "yé ye"), ("老爷", "lǎo ye")],
        "examples": [("爷爷在院子里下棋。", "yé ye zài yuàn zi lǐ xià qí")],
    },
    "奶": {
        "words": [("奶奶", "nǎi nai"), ("牛奶", "niú nǎi"), ("奶粉", "nǎi fěn")],
        "examples": [("奶奶给我煮了牛奶。", "nǎi nai gěi wǒ zhǔ le niú nǎi")],
    },
    "哥": {
        "words": [("哥哥", "gē ge"), ("大哥", "dà gē")],
        "examples": [("哥哥教我写字。", "gē ge jiāo wǒ xiě zì")],
    },
    "姐": {
        "words": [("姐姐", "jiě jie"), ("大姐", "dà jiě")],
        "examples": [("姐姐会弹钢琴。", "jiě jie huì tán gāng qín")],
    },
    "弟": {
        "words": [("弟弟", "dì di"), ("兄弟", "xiōng dì")],
        "examples": [("弟弟在搭积木。", "dì di zài dā jī mù")],
    },
    "妹": {
        "words": [("妹妹", "mèi mei"), ("姐妹", "jiě mèi")],
        "examples": [("妹妹笑得真甜。", "mèi mei xiào de zhēn tián")],
    },
    "家": {
        "words": [("回家", "huí jiā"), ("家人", "jiā ren"), ("家乡", "jiā xiāng")],
        "examples": [("我们一起回家吧。", "wǒ men yī qǐ huí jiā ba")],
    },
    "子": {
        "words": [("孩子", "hái zi"), ("儿子", "ér zi"), ("子弹", "zǐ dàn")],
        "examples": [("孩子在外面玩耍。", "hái zi zài wài miàn wán shuǎ")],
    },
    "女": {
        "words": [("女孩", "nǚ hái"), ("女儿", "nǚ ér"), ("女生", "nǚ shēng")],
        "examples": [("小女孩在跳舞。", "xiǎo nǚ hái zài tiào wǔ")],
    },
    "心": {
        "words": [("开心", "kāi xīn"), ("爱心", "ài xīn"), ("小心", "xiǎo xīn")],
        "examples": [("今天我过得很开心。", "jīn tiān wǒ guò de hěn kāi xīn")],
    },
    "爱": {
        "words": [("爱心", "ài xīn"), ("可爱", "kě ài"), ("热爱", "rè ài")],
        "examples": [("我爱我的家。", "wǒ ài wǒ de jiā")],
    },
    "书": {
        "words": [("看书", "kàn shū"), ("书本", "shū běn"), ("读书", "dú shū")],
        "examples": [("我喜欢看图画书。", "wǒ xǐ huan kàn tú huà shū")],
    },
    "本": {
        "words": [("书本", "shū běn"), ("本来", "běn lái"), ("本子", "běn zi")],
        "examples": [("我有一本新本子。", "wǒ yǒu yì běn xīn běn zi")],
    },
    "笔": {
        "words": [("铅笔", "qiān bǐ"), ("画笔", "huà bǐ"), ("毛笔", "máo bǐ")],
        "examples": [("我用铅笔写字。", "wǒ yòng qiān bǐ xiě zì")],
    },
    "校": {
        "words": [("学校", "xué xiào"), ("校园", "xiào yuán"), ("校长", "xiào zhǎng")],
        "examples": [("我在学校学写字。", "wǒ zài xué xiào xué xiě zì")],
    },
    "学": {
        "words": [("学习", "xué xí"), ("学生", "xué sheng"), ("学校", "xué xiào")],
        "examples": [("我要好好学习。", "wǒ yào hǎo hǎo xué xí")],
    },
    "友": {
        "words": [("朋友", "péng yǒu"), ("好友", "hǎo yǒu"), ("友谊", "yǒu yì")],
        "examples": [("他是我的好朋友。", "tā shì wǒ de hǎo péng yǒu")],
    },
    "吃": {
        "words": [("吃饭", "chī fàn"), ("好吃", "hǎo chī"), ("吃东西", "chī dōng xi")],
        "examples": [("到吃饭时间了。", "dào chī fàn shí jiān le")],
    },
    "喝": {
        "words": [("喝水", "hē shuǐ"), ("喝茶", "hē chá"), ("喝奶", "hē nǎi")],
        "examples": [("小朋友要常喝水。", "xiǎo péng yǒu yào cháng hē shuǐ")],
    },
    "玩": {
        "words": [("玩具", "wán jù"), ("玩耍", "wán shuǎ"), ("游玩", "yóu wán")],
        "examples": [("我们在公园里玩耍。", "wǒ men zài gōng yuán lǐ wán shuǎ")],
    },
    "乐": {
        "words": [("快乐", "kuài lè"), ("音乐", "yīn yuè"), ("欢乐", "huān lè")],
        "examples": [("祝你生日快乐! ", "zhù nǐ shēng rì kuài lè")],
    },
    "车": {
        "words": [("汽车", "qì chē"), ("火车", "huǒ chē"), ("小车", "xiǎo chē")],
        "examples": [("马路上车很多。", "mǎ lù shàng chē hěn duō")],
    },
    "马": {
        "words": [("小马", "xiǎo mǎ"), ("马车", "mǎ chē"), ("骑马", "qí mǎ")],
        "examples": [("我最喜欢小马。", "wǒ zuì xǐ huan xiǎo mǎ")],
    },
    "牛": {
        "words": [("小牛", "xiǎo niú"), ("牛奶", "niú nǎi"), ("黄牛", "huáng niú")],
        "examples": [("小牛在草地上吃草。", "xiǎo niú zài cǎo dì shàng chī cǎo")],
    },
    "羊": {
        "words": [("小羊", "xiǎo yáng"), ("山羊", "shān yáng"), ("绵羊", "mián yáng")],
        "examples": [("小白羊在山坡上。", "xiǎo bái yáng zài shān pō shàng")],
    },
    "上": {
        "words": [("上学", "shàng xué"), ("上来", "shàng lái"), ("上面", "shàng miàn")],
        "examples": [("我高高兴兴去上学。", "wǒ gāo gāo xìng xìng qù shàng xué")],
    },
    "下": {
        "words": [("下来", "xià lái"), ("下雨", "xià yǔ"), ("下面", "xià miàn")],
        "examples": [("今天下午会下雨。", "jīn tiān xià wǔ huì xià yǔ")],
    },
    "大": {
        "words": [("大象", "dà xiàng"), ("大人", "dà ren"), ("大家", "dà jiā")],
        "examples": [("大象有长长的鼻子。", "dà xiàng yǒu cháng cháng de bí zi")],
    },
    "小": {
        "words": [("小猫", "xiǎo māo"), ("小朋友", "xiǎo péng yǒu"), ("小鸟", "xiǎo niǎo")],
        "examples": [("我家养了一只小猫。", "wǒ jiā yǎng le yì zhī xiǎo māo")],
    },
    "中": {
        "words": [("中国", "zhōng guó"), ("中间", "zhōng jiān"), ("中午", "zhōng wǔ")],
        "examples": [("我是一名中国小朋友。", "wǒ shì yì míng zhōng guó xiǎo péng yǒu")],
    },
    "文": {
        "words": [("中文", "zhōng wén"), ("文字", "wén zì"), ("文化", "wén huà")],
        "examples": [("我在学中文。", "wǒ zài xué zhōng wén")],
    },
    "王": {
        "words": [("国王", "guó wáng"), ("王子", "wáng zǐ"), ("女王", "nǚ wáng")],
        "examples": [("故事书里有个小王子。", "gù shi shū lǐ yǒu gè xiǎo wáng zǐ")],
    },
    "开": {
        "words": [("开门", "kāi mén"), ("开心", "kāi xīn"), ("开花", "kāi huā")],
        "examples": [("花儿开了真漂亮。", "huā er kāi le zhēn piào liang")],
    },
    "云": {
        "words": [("白云", "bái yún"), ("云朵", "yún duǒ"), ("乌云", "wū yún")],
        "examples": [("天上飘着一朵白云。", "tiān shàng piāo zhe yì duǒ bái yún")],
    },
    "雨": {
        "words": [("下雨", "xià yǔ"), ("雨水", "yǔ shuǐ"), ("大雨", "dà yǔ")],
        "examples": [("下雨了要撑伞。", "xià yǔ le yào chēng sǎn")],
    },
    "风": {
        "words": [("大风", "dà fēng"), ("微风", "wēi fēng"), ("台风", "tái fēng")],
        "examples": [("今天的风很凉爽。", "jīn tiān de fēng hěn liáng shuǎng")],
    },
    "花": {
        "words": [("花朵", "huā duǒ"), ("鲜花", "xiān huā"), ("花园", "huā yuán")],
        "examples": [("花园里的花开了。", "huā yuán lǐ de huā kāi le")],
    },
    "草": {
        "words": [("小草", "xiǎo cǎo"), ("草地", "cǎo dì"), ("青草", "qīng cǎo")],
        "examples": [("绿绿的小草真可爱。", "lǜ lǜ de xiǎo cǎo zhēn kě ài")],
    },
    "树": {
        "words": [("大树", "dà shù"), ("树木", "shù mù"), ("树叶", "shù yè")],
        "examples": [("大树上有一只小鸟。", "dà shù shàng yǒu yì zhī xiǎo niǎo")],
    },
    "叶": {
        "words": [("树叶", "shù yè"), ("绿叶", "lǜ yè"), ("落叶", "luò yè")],
        "examples": [("秋天的落叶真好看。", "qiū tiān de luò yè zhēn hǎo kàn")],
    },
    "虫": {
        "words": [("虫子", "chóng zi"), ("毛毛虫", "máo máo chóng"), ("小虫", "xiǎo chóng")],
        "examples": [("树叶上有一只小虫。", "shù yè shàng yǒu yì zhī xiǎo chóng")],
    },
    "鸟": {
        "words": [("小鸟", "xiǎo niǎo"), ("飞鸟", "fēi niǎo"), ("鸟儿", "niǎo er")],
        "examples": [("小鸟在树上唱歌。", "xiǎo niǎo zài shù shàng chàng gē")],
    },
    "鱼": {
        "words": [("小鱼", "xiǎo yú"), ("金鱼", "jīn yú"), ("鱼儿", "yú er")],
        "examples": [("池塘里有许多小鱼。", "chí táng lǐ yǒu xǔ duō xiǎo yú")],
    },
    "米": {
        "words": [("米饭", "mǐ fàn"), ("大米", "dà mǐ"), ("小米", "xiǎo mǐ")],
        "examples": [("我喜欢吃白米饭。", "wǒ xǐ huan chī bái mǐ fàn")],
    },
    "面": {
        "words": [("面条", "miàn tiáo"), ("面包", "miàn bāo"), ("脸面", "liǎn miàn")],
        "examples": [("妈妈煮了面条。", "mā ma zhǔ le miàn tiáo")],
    },
    "果": {
        "words": [("水果", "shuǐ guǒ"), ("苹果", "píng guǒ"), ("果汁", "guǒ zhī")],
        "examples": [("我喜欢吃水果。", "wǒ xǐ huan chī shuǐ guǒ")],
    },
    "好": {
        "words": [("好的", "hǎo de"), ("好看", "hǎo kàn"), ("好吃", "hǎo chī")],
        "examples": [("这本书真好看。", "zhè běn shū zhēn hǎo kàn")],
    },
    "看": {
        "words": [("看书", "kàn shū"), ("看见", "kàn jiàn"), ("好看", "hǎo kàn")],
        "examples": [("我在家里看书。", "wǒ zài jiā lǐ kàn shū")],
    },
    "听": {
        "words": [("听话", "tīng huà"), ("听见", "tīng jiàn"), ("好听", "hǎo tīng")],
        "examples": [("我喜欢听故事。", "wǒ xǐ huan tīng gù shi")],
    },
    "说": {
        "words": [("说话", "shuō huà"), ("说明", "shuō míng"), ("传说", "chuán shuō")],
        "examples": [("老师在讲故事。", "lǎo shī zài jiǎng gù shi")],
    },
    "走": {
        "words": [("走路", "zǒu lù"), ("走走", "zǒu zǒu"), ("走开", "zǒu kāi")],
        "examples": [("我们一起走路回家。", "wǒ men yī qǐ zǒu lù huí jiā")],
    },
    "跑": {
        "words": [("跑步", "pǎo bù"), ("奔跑", "bēn pǎo"), ("起跑", "qǐ pǎo")],
        "examples": [("小朋友在操场上跑步。", "xiǎo péng yǒu zài cāo chǎng shàng pǎo bù")],
    },
    "飞": {
        "words": [("飞机", "fēi jī"), ("飞翔", "fēi xiáng"), ("起飞", "qǐ fēi")],
        "examples": [("小鸟在天上飞。", "xiǎo niǎo zài tiān shàng fēi")],
    },
    "笑": {
        "words": [("微笑", "wēi xiào"), ("笑话", "xiào hua"), ("大笑", "dà xiào")],
        "examples": [("小朋友笑得真开心。", "xiǎo péng yǒu xiào de zhēn kāi xīn")],
    },
    "哭": {
        "words": [("哭声", "kū shēng"), ("哭泣", "kū qì"), ("哭哭", "kū kū")],
        "examples": [("小弟弟摔了一跤哭了。", "xiǎo dì di shuāi le yì jiāo kū le")],
    },
    "白": {
        "words": [("白色", "bái sè"), ("白云", "bái yún"), ("白天", "bái tiān")],
        "examples": [("天上的云白白的。", "tiān shàng de yún bái bái de")],
    },
    "红": {
        "words": [("红色", "hóng sè"), ("红花", "hóng huā"), ("口红", "kǒu hóng")],
        "examples": [("花园里开了红花。", "huā yuán lǐ kāi le hóng huā")],
    },
    "黄": {
        "words": [("黄色", "huáng sè"), ("黄河", "huáng hé"), ("黄瓜", "huáng guā")],
        "examples": [("秋天的叶子黄黄的。", "qiū tiān de yè zi huáng huáng de")],
    },
    "绿": {
        "words": [("绿色", "lǜ sè"), ("绿草", "lǜ cǎo"), ("绿叶", "lǜ yè")],
        "examples": [("春天到了草儿绿了。", "chūn tiān dào le cǎo er lǜ le")],
    },
    "多": {
        "words": [("很多", "hěn duō"), ("多少", "duō shǎo"), ("许多", "xǔ duō")],
        "examples": [("今天来了许多小朋友。", "jīn tiān lái le xǔ duō xiǎo péng yǒu")],
    },
    "少": {
        "words": [("少数", "shǎo shù"), ("减少", "jiǎn shǎo"), ("多少", "duō shǎo")],
        "examples": [("蛋糕只剩下一点点了。", "dàn gāo zhǐ shèng xià yì diǎn diǎn le")],
    },
    "高": {
        "words": [("高大", "gāo dà"), ("高兴", "gāo xìng"), ("高山", "gāo shān")],
        "examples": [("今天我过得很高兴。", "jīn tiān wǒ guò de hěn gāo xìng")],
    },
    "长": {
        "words": [("很长", "hěn cháng"), ("长高", "zhǎng gāo"), ("长大", "zhǎng dà")],
        "examples": [("小树苗慢慢长大。", "xiǎo shù miáo màn màn zhǎng dà")],
    },
    "圆": {
        "words": [("圆形", "yuán xíng"), ("圆圈", "yuán quān"), ("团圆", "tuán yuán")],
        "examples": [("中秋节的月亮真圆。", "zhōng qiū jié de yuè liang zhēn yuán")],
    },
    "方": {
        "words": [("方形", "fāng xíng"), ("方向", "fāng xiàng"), ("地方", "dì fang")],
        "examples": [("这个盒子是方形的。", "zhè ge hé zi shì fāng xíng de")],
    },
    "点": {
        "words": [("一点", "yì diǎn"), ("点点", "diǎn dian"), ("点头", "diǎn tóu")],
        "examples": [("再吃一点点就好。", "zài chī yì diǎn diǎn jiù hǎo")],
    },
    "名": {
        "words": [("名字", "míng zi"), ("姓名", "xìng míng"), ("有名", "yǒu míng")],
        "examples": [("请告诉我你的名字。", "qǐng gào su wǒ nǐ de míng zi")],
    },
    "儿": {
        "words": [("儿子", "ér zi"), ("儿童", "ér tóng"), ("女儿", "nǚ ér")],
        "examples": [("我是妈妈的儿子。", "wǒ shì mā ma de ér zi")],
    },
    "明": {
        "words": [("明天", "míng tiān"), ("明白", "míng bai"), ("明亮", "míng liàng")],
        "examples": [("明天我们去公园。", "míng tiān wǒ men qù gōng yuán")],
    },
    "今": {
        "words": [("今天", "jīn tiān"), ("今年", "jīn nián"), ("今晚", "jīn wǎn")],
        "examples": [("今天是我的生日。", "jīn tiān shì wǒ de shēng rì")],
    },
    "年": {
        "words": [("一年", "yì nián"), ("新年", "xīn nián"), ("去年", "qù nián")],
        "examples": [("新年到了真热闹。", "xīn nián dào le zhēn rè nao")],
    },
    "月": {
        "words": [("月亮", "yuè liang"), ("月份", "yuè fèn"), ("月饼", "yuè bǐng")],
        "examples": [("十五的月亮圆又亮。", "shí wǔ de yuè liang yuán yòu liàng")],
    },
    "日": {
        "words": [("日子", "rì zi"), ("生日", "shēng rì"), ("日记", "rì jì")],
        "examples": [("今天是我盼望的日子。", "jīn tiān shì wǒ pàn wàng de rì zi")],
    },
    "时": {
        "words": [("时间", "shí jiān"), ("时候", "shí hou"), ("小时", "xiǎo shí")],
        "examples": [("现在是什么时间? ", "xiàn zài shì shén me shí jiān")],
    },
    "分": {
        "words": [("分钟", "fēn zhōng"), ("分开", "fēn kāi"), ("分数", "fēn shù")],
        "examples": [("再过五分钟就到。", "zài guò wǔ fēn zhōng jiù dào")],
    },
    "半": {
        "words": [("一半", "yí bàn"), ("半天", "bàn tiān"), ("半夜", "bàn yè")],
        "examples": [("苹果我吃了一半。", "píng guǒ wǒ chī le yí bàn")],
    },
    "左": {
        "words": [("左边", "zuǒ bian"), ("左手", "zuǒ shǒu"), ("左右", "zuǒ yòu")],
        "examples": [("教室左边是窗户。", "jiào shì zuǒ bian shì chuāng hu")],
    },
    "右": {
        "words": [("右边", "yòu bian"), ("右手", "yòu shǒu"), ("左右", "zuǒ yòu")],
        "examples": [("我家右边是公园。", "wǒ jiā yòu bian shì gōng yuán")],
    },
    "前": {
        "words": [("前面", "qián miàn"), ("以前", "yǐ qián"), ("前进", "qián jìn")],
        "examples": [("教室前面有一块黑板。", "jiào shì qián miàn yǒu yí kuài hēi bǎn")],
    },
    "后": {
        "words": [("后面", "hòu miàn"), ("以后", "yǐ hòu"), ("后来", "hòu lái")],
        "examples": [("我排在小明后面。", "wǒ pái zài xiǎo míng hòu miàn")],
    },
    "里": {
        "words": [("里面", "lǐ miàn"), ("屋里", "wū lǐ"), ("心里", "xīn lǐ")],
        "examples": [("盒子里装着一颗糖。", "hé zi lǐ zhuāng zhe yì kē táng")],
    },
    "外": {
        "words": [("外面", "wài miàn"), ("门外", "mén wài"), ("国外", "guó wài")],
        "examples": [("外面下着毛毛雨。", "wài miàn xià zhe máo máo yǔ")],
    },
}


def build_entry(char: str) -> dict:
    """根据字构造 JSON 条目。无 curated 数据时退化为只含 char 的对象。"""
    info = ENTRIES.get(char)
    if info is None:
        return {"char": char}
    return {
        "char": char,
        "words": [{"word": w, "pinyin": p} for (w, p) in info["words"]],
        "examples": [{"sentence": s, "pinyin": p} for (s, p) in info["examples"]],
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="为 character_sets.json 中**字典命中的**常用字生成词组与例句"
    )
    parser.add_argument(
        "--limit", type=int, default=0,
        help="最多处理多少个字(0 = 不限制,处理字典命中的全部)。"
             "注意:v1.5.0 起不再按'前 N 个下标'切片 —— 那会导致字典命中的字被漏掉、"
             "而字典外的字被写成没有内容的空对象。",
    )
    parser.add_argument("--dry-run", action="store_true", help="仅打印前几个条目,不写文件")
    parser.add_argument("--path", type=Path, default=DEFAULT_JSON, help="字库文件路径")
    args = parser.parse_args()

    if not args.path.exists():
        print(f"[ERROR] 字库文件不存在: {args.path}", file=sys.stderr)
        return 1

    with args.path.open("r", encoding="utf-8") as fp:
        data = json.load(fp)

    easy = data.get("easy", [])
    if not isinstance(easy, list):
        print("[ERROR] easy 字段不是数组", file=sys.stderr)
        return 1

    limit = args.limit if args.limit and args.limit > 0 else None
    converted = 0      # 由字符串/空对象补成完整条目
    already = 0        # 已有词组,跳过
    not_in_dict = 0    # 字典里没有 -> 保持原样(不再写空对象)

    for i, item in enumerate(easy):
        if isinstance(item, str):
            ch = item.strip()
        elif isinstance(item, dict):
            ch = str(item.get("char", "")).strip()
        else:
            print(f"[WARN] 第 {i} 项格式异常: {item!r}", file=sys.stderr)
            continue

        if not ch:
            continue

        # 关键修复:字典里没有这个字就**原样保留**。
        # 旧实现会把它写成 {"char": "X"}(无词组无例句)—— 既没内容,
        # 又把原来的字符串格式改写了一遍,产生 44 个无意义的空对象。
        if ch not in ENTRIES:
            not_in_dict += 1
            continue

        if isinstance(item, dict) and item.get("words"):
            already += 1
            continue

        if limit is not None and converted >= limit:
            break

        easy[i] = build_entry(ch)
        converted += 1

    if args.dry_run:
        print(
            f"[DRY-RUN] 将补全 {converted} 项;已有词组跳过 {already} 项;"
            f"字典未覆盖保持原样 {not_in_dict} 项"
        )
        for item in easy[:3]:
            print(json.dumps(item, ensure_ascii=False, indent=2))
        return 0

    # 直接覆盖写回(其他字段保持原状)
    with args.path.open("w", encoding="utf-8") as fp:
        json.dump(data, fp, ensure_ascii=False, indent=2)
        fp.write("\n")

    print(
        f"[OK] 补全 {converted} 项;已有词组跳过 {already} 项;"
        f"字典未覆盖保持原样 {not_in_dict} 项;文件已写回: {args.path}"
    )
    print("[提示] 校验字库完整性请运行: python scripts/verify_character_sets.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())

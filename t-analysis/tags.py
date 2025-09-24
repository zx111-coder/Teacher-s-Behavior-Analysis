import json
AVA_TAGS_DICT = {'1': dict(aname='头部姿态', type=2, options={'0': '正向',
                                                          '1': '低头', '2': '侧面', '3': '背面', '4': '抬头'}, default_option_id="", anchor_id='FILE1_Z0_XY1'),

                 '2': dict(aname='手臂动作', type=2, options={'0': '斜向上指',
                                                          '1': '垂直', '2': '向前指', '3': '比划'}, default_option_id="", anchor_id='FILE1_Z0_XY1'),

                 '3': dict(aname='教学评估', type=2, options={
                     '0': '坐着',
                     '1': '站直', 
                     '2': '走动',
                     '3': '拿物体',
                     '4':"看学生",
                     '5':"看电脑屏幕",
                     '6':"看投影",
                     '7':"看黑板",
                     '8':"写板书",
                     '9':"讲解",
                     '10':"走下讲台",
                     }, default_option_id="", anchor_id='FILE1_Z0_XY1'),
                   '4': dict(aname='表情', type=2, options={
                     '0': '笑',
                     '1': '严肃', 
                     }, default_option_id="", anchor_id='FILE1_Z0_XY1'),
                 }

database_keyvalue = {
    'write_blackboard':{'name':'','indexs':[18,],'tagAmount':0,'inferenceAmount':0,'wrongAmount':0,'missAmount':0,'precision':0},
    'explanation':{'name':'','indexs':[9,19],'tagAmount':0,'inferenceAmount':0,'wrongAmount':0,'missAmount':0,'precision':0},
    'look_at_student':{'name':'','indexs':[14,],'tagAmount':0,'inferenceAmount':0,'wrongAmount':0,'missAmount':0,'precision':0},
    'look_at_computer':{'name':'','indexs':[15,],'tagAmount':0,'inferenceAmount':0,'wrongAmount':0,'missAmount':0,'precision':0},
    'look_at_blackboard':{'name':'','indexs':[17,16],'tagAmount':0,'inferenceAmount':0,'wrongAmount':0,'missAmount':0,'precision':0},
    'sit_times':{'name':'','indexs':[10,],'tagAmount':0,'inferenceAmount':0,'wrongAmount':0,'missAmount':0,'precision':0},
}

demo_av = {
					"1": "0,1",
					"2": "",
					"3": "0,3,5,9",
					"4": "1"
				}

from functools import reduce
class Tags:
    
    def __init__(self,AVA_TAGS_DICT):
        self.AVA_TAGS_DICT = AVA_TAGS_DICT
        self.projection = False
        self.indexProjection = False
        self.make_projection()
        self.make_index_projection()
        self.metadata = {}


    
    def test_make_metadata(self,num):
        AVA_TAGS_DICT = self.AVA_TAGS_DICT
        
        for i in range(num):
            key = 'image' + str(i+1) + '_1'
            av = {}
            for k in AVA_TAGS_DICT: 
                av[k] = ""
            self.metadata[key] = {
                "vid": str(i + 1),
                "xy":[],
                "av":av,
                "flg": 0,
                "z": []
            }
        #print(self.metadata)



    def set_metadata(self,metadata):
        AVA_TAGS_DICT = self.AVA_TAGS_DICT
        self.metadata = metadata
        

    def make_tag(self,pic_index,box_index,action):
        try:
            av_key =  'image' + str(pic_index+1) + '_' + str(box_index+1)
            action = int(action)
            keys = self.projection[action]
            #print('keys:',keys)
            av = self.metadata[av_key]['av']
            av[keys[0]]= self.add_av(av[keys[0]],keys[1])            
            self.metadata[av_key]['av'] = av
        except:
            pass
        # #print(av_key + ':',av)
        
        
        
    def make_projection(self):
        AVA_TAGS_DICT = self.AVA_TAGS_DICT
        id=1
        result = {}
        for tc in AVA_TAGS_DICT:
            tcdic = AVA_TAGS_DICT[tc]
            tcdicoptions = tcdic['options']
            for o in tcdicoptions:
                name = tcdicoptions[o]
                key = int(id)
                result[key] = [tc,o]
                id= id+1  
        self.projection = result
    
    def add_av(self,av,tag):
        tag = str(tag)
        av_array = av.split(',')
        is_in = tag in av_array
        if not is_in:
            av_array.append(tag)
        avstr = reduce(lambda x, y:  str(x) + ',' + str(y)   , av_array)
        if avstr[0] == ',':
            avstr = avstr[1:]
        if avstr[-1] == ',':
            avstr = avstr[:1]
        return avstr

    def add_80action_to_custom_action(self,pic_index,index,_80_action_num):
        _80_action_num = str(_80_action_num)
        print('_80_action_num +++++++++',_80_action_num)
        project_num = False
        mapping = {
            "11":"10", #坐
            "12":"11", #站立
            "14":"12", #走
            "17":"13", #拿
            "34":"18", #写板书
            "61":"15", #看（屏幕、电视）
            "63":"18", #写板书
            "69":"9", #比划
            "79":"19", #讲解
            "80":"14", #看学生
        }
        
        if _80_action_num in mapping:
            
            project_num = mapping[_80_action_num]
            self.make_tag(pic_index,index,project_num)
    
    
    def log(self):
        result = self.metadata
        # print(json.dumps(result))
        with open('./metadata.json', 'w') as file:
            file.write(json.dumps(result))  # 将内容写入文件 
        return result

    def make_index_projection(self):
        AVA_TAGS_DICT = self.AVA_TAGS_DICT
        id=1
        result = {}
        for tc in AVA_TAGS_DICT:
            tcdic = AVA_TAGS_DICT[tc]
            tcdicoptions = tcdic['options']
            for o in tcdicoptions:
                name = tcdicoptions[o]
                key = int(id)
                result[key] = name
                id= id+1  
        self.indexProjection = result

    def makeResultDic(self):
        
        return

    def parseAv(self,av):
        global database_keyvalue
        testdic = {}
        testarray = []
        keyindexs = []
        id = 1 
        for i in av:
            option_str = av[i]
            if option_str:
                option_str_array = option_str.split(',')
                for osa in option_str_array:
                    keyindexs.append([str(id),osa])
            id += 1
        print('av results:',keyindexs)
        projections = self.projection
        for pkey in projections:
            p = projections[pkey]
            for avi in keyindexs:
                if p[0] == avi[0] and p[1] == avi[1]:
                    testarray.append(pkey)
        
        print('av testarray:',testarray)    
        for ta in testarray:
            for dk in database_keyvalue:
                item = database_keyvalue[dk]
                indexs = item['indexs']
                if int(ta) in indexs:
                    item['tagAmount'] += 1
                    print(dk,"tag !!!")
                    testdic[dk] = 1
     
        print('av testdic:',testdic)        
        return testdic

    def compareTag(self,tagAv,inferenceAv,resultDic):
        return

tags = Tags(AVA_TAGS_DICT)
print(tags.projection)
# print(tags.indexProjection)
tags.parseAv(demo_av)
# tags.test_make_metadata(27)
# tags.make_tag(27,"4")
# tags.make_tag(27,13)
# tags.make_tag(27,15)
# tags.add_80action_to_custom_action(27,11)
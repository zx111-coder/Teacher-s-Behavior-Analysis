import argparse
import copy as cp
import os
import os.path as osp
import shutil

import cv2
import mmcv
import numpy as np
import torch
from mmcv import DictAction
from mmcv.runner import load_checkpoint

from mmpose.datasets import DatasetInfo

import requests
import json

import warnings


from PIL import Image, ImageFont, ImageDraw

from process import VideoProcess
from skeleton import Skeleton



from mmaction.models import build_detector
from mmaction.utils import import_module_error_func

from mmpose.apis import (inference_top_down_pose_model, init_pose_model,vis_pose_result)

try:
    from mmdet.apis import inference_detector, init_detector
except (ImportError, ModuleNotFoundError):

    @import_module_error_func('mmdet')
    def inference_detector(*args, **kwargs):
        pass

    @import_module_error_func('mmdet')
    def init_detector(*args, **kwargs):
        pass


try:
    import moviepy.editor as mpy
except ImportError:
    raise ImportError('Please install moviepy to enable output file')

FONTFACE = cv2.FONT_HERSHEY_DUPLEX
FONTSCALE = 0.5
FONTCOLOR = (255, 255, 255)  # BGR, white
MSGCOLOR = (128, 128, 128)  # BGR, gray
THICKNESS = 1
LINETYPE = 1

skeletonChecker =  Skeleton()

font_path = "/home/user3/mmaction2_YF_2/demo/SourceHanSansSC-Bold.otf"
font = ImageFont.truetype(font_path, 12)

det_model = False
action_model = False
action_model_slowfast = False

skeleton_model = None
skeleton_dataset = None
skeleton_dataset_info = None

frame_paths = False
original_frames = False

def hex2color(h):
    """Convert the 6-digit hex string to tuple of 3 int value (RGB)"""
    return (int(h[:2], 16), int(h[2:4], 16), int(h[4:], 16))


plate_blue = '03045e-023e8a-0077b6-0096c7-00b4d8-48cae4'
plate_blue = plate_blue.split('-')
plate_blue = [hex2color(h) for h in plate_blue]
plate_green = '004b23-006400-007200-008000-38b000-70e000'
plate_green = plate_green.split('-')
plate_green = [hex2color(h) for h in plate_green]

analyze_result = {
        "hand_point_down":0,
        "head_point_front":0,
        "head_point_side":0,
        "head_point_back":0,
        "hand_point_up":0,
        "hand_point_horizon":0,
        "hand_point_front":0,
        "look_at_student":0,
        "look_at_computer":0,
        "write_blackboard":0,
        "explanation":0,
        "look_at_blackboard":0,
        "look_at_projector":0,
        "hand_wave":0,
        "sit_times":0,
        "stand_times":0,
        "walk_times":0,
        "take_times":0,
        "sit_down":0,
        "hand_wave":0,
        "hand_images":'',
        "pose_images":'',
    }

analyze_time_line = []

# 计算教学表现评分11111111111111111111111111111
def calculate_teaching_score(analyze_result, total_frames):
    """更严格的教学表现评分算法"""
    # 计算各项行为的标准化频率（每100帧的次数）
    def calc_freq(count):
        return min(count / total_frames * 100, 100)  # 不超过100
    
    freqs = {
        'explanation': calc_freq(analyze_result['explanation']), # 讲解
        'writing': calc_freq(analyze_result['write_blackboard']), # 板书
        'interaction': calc_freq(analyze_result['look_at_student']), # 学生互动
        'gestures': calc_freq( # 综合手势111111111111111111111111111111111111111111111111111111111111
            analyze_result['hand_point_down'] + analyze_result['hand_point_horizon'] 
        ),
        'posture': calc_freq(analyze_result['stand_times']),  # 站立时间
        'movement': calc_freq(analyze_result['walk_times'])   # 走动次数
    }

    # ============= 更严格的评分标准 =============
    scores = {
        # 讲解：要求更高频率才能得高分
        'explanation': min(freqs['explanation'] / 6 * 20, 20),  
        
        # 板书：增加非线性评分
        'writing': 20 if freqs['writing'] > 25 else 
                 15 if freqs['writing'] > 15 else
                 10 if freqs['writing'] > 8 else
                 5,
        
        # 互动：降低基础分，增加阶梯
        'interaction': min(freqs['interaction'] / 6 * 25, 25),  # 满分从30→25
        
        # 肢体语言：区分不同类型1111111111111111111111111111111111111111111111111111111111
        'gestures': (
            min(freqs['gestures'] / 8 * 15, 15) +  # 基础分15
            (5 if analyze_result['hand_point_front'] > 6 else 0) +  # 向前指额外加分
            (5 if analyze_result['hand_point_up'] > 6 else 0)       # 举手额外加分
        ),
        
        # 新增评分维度（共10分）1111111111111111111111111111111111111111111111
        'posture': min(freqs['posture'] / 50 * 5, 5),  # 站立时间占比
        'movement': min(freqs['movement'] / 15 * 5, 5)  # 适当走动
    }

    total_score = sum(scores.values())
    
    # ============= 评分解释 =============
    explanation = {
        'score_breakdown': scores,
        'comments': [
            f"讲解得分({scores['explanation']}/20): 每100帧讲解{freqs['explanation']:.1f}次",
            f"板书得分({scores['writing']}/20): 每100帧板书{freqs['writing']:.1f}次",
            f"互动得分({scores['interaction']}/25): 每100帧眼神交流{freqs['interaction']:.1f}次",
            f"肢体语言({scores['gestures']}/27): 综合手势频率{freqs['gestures']:.1f}次",
            f"教姿教态({scores['posture']+scores['movement']}/10): "
            f"站立{freqs['posture']:.1f}% 走动{freqs['movement']:.1f}次"
        ],
        'suggestions': []
    }

    # ============= 针对性建议 =============
    if scores['interaction'] < 15:
        explanation['suggestions'].append("建议增加与学生眼神交流（当前每100帧仅%.1f次）" % freqs['interaction'])
    
    if scores['gestures'] < 15:
        explanation['suggestions'].append(
            "建议丰富手势类型（当前：向下%d次/向上%d次/水平%d次）" % (
                analyze_result['hand_point_down'],
                analyze_result['hand_point_up'],
                analyze_result['hand_point_horizon']
            )
        )
    
    if scores['writing'] < 10:
        explanation['suggestions'].append("建议增加板书书写频率")
    
    if freqs['posture'] < 30:
        explanation['suggestions'].append("建议避免长时间坐着讲课")
#11111111111111111111111111111111111111111111111111111111
    if freqs['movement'] < 3:
        explanation['suggestions'].append("建议适当走动，增加课堂活力")
    # ============= 最终分数调整 =============
    # 防止满分（最高98分）
    final_score = min(total_score, 98)
    

    
    return round(final_score, 1), explanation

#该函数通过绘制检测框、标签和骨骼关键点，将预测结果直观地展示在视频帧上，便于分析和展示
def visualize(frames, annotations, plate=plate_blue, max_num=5):
    """Visualize frames with predicted annotations.

    Args:
        frames (list[np.ndarray]): Frames for visualization, note that
            len(frames) % len(annotations) should be 0.
        annotations (list[list[tuple]]): The predicted results.
        plate (str): The plate used for visualization. Default: plate_blue.
        max_num (int): Max number of labels to visualize for a person box.
            Default: 5.

    Returns:
        list[np.ndarray]: Visualized frames.
    """

    assert max_num + 1 <= len(plate)
    plate = [x[::-1] for x in plate]
    frames_ = cp.deepcopy(frames)
    nf, na = len(frames), len(annotations)
    assert nf % na == 0
    nfpa = len(frames) // len(annotations)
    anno = None
    h, w, _ = frames[0].shape
    scale_ratio = np.array([w, h, w, h])
    for i in range(na):
        anno = annotations[i]
        if anno is None:
            continue
        for j in range(nfpa):
            ind = i * nfpa + j
            for ann in anno:
                frame = frames_[ind]
                box = ann[0]
                label = ann[1]
                skeleton = ann[3]
                if not len(label):
                    continue
                score = ann[2]
                box = (box * scale_ratio).astype(np.int64)
                st, ed = tuple(box[:2]), tuple(box[2:])
                cv2.rectangle(frame, st, ed, plate[0], 2)
                pil_img = Image.fromarray(frame)
                cv_img = frame
                for k, lb in enumerate(label):
                    if k >= max_num:
                        break
                    text = abbrev(lb)
                    text = ': '.join([text, str(score[k])])
                    location = (0 + st[0], 18 + k * 18 + st[1])
                    textsize = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, FONTSCALE,
                                               THICKNESS)[0]
                    textwidth = textsize[0]
                    diag0 = (location[0] + textwidth, location[1] - 14)
                    diag1 = (location[0], location[1] + 2)
                    # cv2.rectangle(cv_img, diag0, diag1, plate[k + 1], -1)
                    pil_img = Image.fromarray(cv_img)
                    draw = ImageDraw.Draw(pil_img)
                    draw.text(location, text, font=font, fill=(255, 255, 255))
                    # 将PIL图像转换回OpenCV格式
                    cv_img = np.array(pil_img)
                                # 画骨骼
                cv_img = vis_pose_result(
                skeleton_model,
                cv_img,
                skeleton,
                dataset=skeleton_dataset,
                dataset_info=skeleton_dataset_info,
                kpt_score_thr=0.3,
                radius=4,
                thickness=1,
                show=False,
                out_file=None)
                frames_[ind] = cv_img
    return frames_


def visualize_customer(frames, annotations, plate=plate_blue, max_num=5):
    """Visualize frames with predicted annotations.

    Args:
        frames (list[np.ndarray]): Frames for visualization, note that
            len(frames) % len(annotations) should be 0.
        annotations (list[list[tuple]]): The predicted results.
        plate (str): The plate used for visualization. Default: plate_blue.
        max_num (int): Max number of labels to visualize for a person box.
            Default: 5.

    Returns:
        list[np.ndarray]: Visualized frames.
    """

    assert max_num + 1 <= len(plate)
    plate = [x[::-1] for x in plate]
    frames_ = cp.deepcopy(frames)
    nf, na = len(frames), len(annotations)
    assert nf % na == 0
    nfpa = len(frames) // len(annotations)
    anno = None
    h, w, _ = frames[0].shape
    scale_ratio = np.array([w, h, w, h])
    for i in range(na):
        anno = annotations[i]
        if anno is None:
            continue
        for j in range(nfpa):
            ind = i * nfpa + j
            for ann in anno:
                frame = frames_[ind]
                box = ann[0]
                label = ann[1]
                if not len(label):
                    continue
                score = ann[2]
                box = (box * scale_ratio).astype(np.int64)
                st, ed = tuple(box[:2]), tuple(box[2:])
                cv2.rectangle(frame, st, ed, plate[0], 2)
                pil_img = Image.fromarray(frame)
                cv_img = frame
                for k, lb in enumerate(label):
                    if k >= max_num:
                        break
                    text = abbrev(lb)
                    text = ': '.join([text, str(score[k])])
                    location = (0 + st[0], 18 + k * 18 + st[1])
                    textsize = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, FONTSCALE,
                                               THICKNESS)[0]
                    textwidth = textsize[0]
                    diag0 = (location[0] + textwidth, location[1] - 14)
                    diag1 = (location[0], location[1] + 2)
                    # cv2.rectangle(cv_img, diag0, diag1, plate[k + 1], -1)
                    pil_img = Image.fromarray(cv_img)
                    draw = ImageDraw.Draw(pil_img)
                    draw.text(location, text, font=font, fill=(255, 255, 255))
                    # 将PIL图像转换回OpenCV格式
                    cv_img = np.array(pil_img)
                frames_[ind] = cv_img
    return frames_


def parse_args():
    parser = argparse.ArgumentParser(description='MMAction2 demo')

    parser.add_argument(
        '--course_id',
        default=(''),
        help='course to analyze')
    parser.add_argument('--gt_bbox_file', help='The path of gt_bbox file')
    parser.add_argument(
        '--callback_url',
        default=(''),
        help='save data to backend')
    parser.add_argument(
        '--out-folder',
        default='tmp',
        help='output filename')
    
    # 添加新参数
    parser.add_argument(
        '--video_id',
        default=(''))
    parser.add_argument(
        '--result-json',
        default='tmp',
        help='指定分析结果JSON文件的保存路径')

    # region sleleton
    parser.add_argument('--pose_config',
                        default=('/home/user3/mmaction2_YF_2/configs_pose/body/2d_kpt_sview_rgb_img/topdown_heatmap/coco/hrnet_w48_coco_256x192.py'),
                        help='Config file for detection')
    parser.add_argument('--pose_checkpoint',
                        default=('https://download.openmmlab.com/mmpose/top_down/hrnet/hrnet_w48_coco_256x192-b9e0b3ab_20200708.pth'),
                        help='Checkpoint file')
    # endregion
    
    # region customer
    parser.add_argument(
        '--config',
        default=('/home/user3/mmaction2_YF_2/configs/detection/ava/slowonly_omnisource_pretrained_r101_8x8x1_20e_ava_rgb.py'),
        help='spatio temporal detection config file path')
    parser.add_argument(
        '--checkpoint',
        default=('https://download.openmmlab.com/mmaction/detection/ava/'
                 'slowonly_omnisource_pretrained_r101_8x8x1_20e_ava_rgb/'
                 'slowonly_omnisource_pretrained_r101_8x8x1_20e_ava_rgb'
                 '_20201217-16378594.pth'),
        help='spatio temporal detection checkpoint file/url')
    parser.add_argument(
        '--cfg-options',
        nargs='+',
        action=DictAction,
        default={},
        help='override some settings in the used config, the key-value pair '
        'in xxx=yyy format will be merged into config file. For example, '
        "'--cfg-options model.backbone.depth=18 model.backbone.with_cp=True'")
    parser.add_argument(
        '--label-map',
        default='tools/data/ava/label_map2.txt',
        help='label map file')
    # end region

    # region slowfast
    parser.add_argument(
        '--configslowfast',
        default=('/home/user3/mmaction2_YF_2/configs/detection/ava/slowonly_omnisource_pretrained_r101_8x8x1_20e_ava_rgb.py'),
        help='spatio temporal detection config file path')
    parser.add_argument(
        '--checkpointslowfast',
        default=('https://download.openmmlab.com/mmaction/detection/ava/'
                 'slowonly_omnisource_pretrained_r101_8x8x1_20e_ava_rgb/'
                 'slowonly_omnisource_pretrained_r101_8x8x1_20e_ava_rgb'
                 '_20201217-16378594.pth'),
        help='spatio temporal detection checkpoint file/url')
    parser.add_argument(
        '--cfg-optionsslowfast',
        nargs='+',
        action=DictAction,
        default={},
        help='override some settings in the used config, the key-value pair '
        'in xxx=yyy format will be merged into config file. For example, '
        "'--cfg-options model.backbone.depth=18 model.backbone.with_cp=True'")
    parser.add_argument(
        '--label-mapslowfast',
        default='/home/user3/mmaction2_YF_2/tools/data/ava/label_map.txt',
        help='label map file')
    # end region
    parser.add_argument(
        '--det-config',
        default='/home/user3/mmaction2_YF_2/demo/faster_rcnn_r50_fpn_2x_coco.py',
        help='human detection config file path (from mmdet)')
    parser.add_argument(
        '--det-checkpoint',
        default=('http://download.openmmlab.com/mmdetection/v2.0/faster_rcnn/'
                 'faster_rcnn_r50_fpn_2x_coco/'
                 'faster_rcnn_r50_fpn_2x_coco_'
                 'bbox_mAP-0.384_20200504_210434-a5d8aa15.pth'),
        help='human detection checkpoint file/url')
    parser.add_argument(
        '--det-score-thr',
        type=float,
        default=0.9,
        help='the threshold of human detection score')
    parser.add_argument(
        '--action-score-thr',
        type=float,
        default=0.3,
        help='the threshold of human action score')
    parser.add_argument('--video', help='video file/url')

    parser.add_argument(
        '--device', type=str, default='cuda:0', help='CPU/CUDA device option')
    parser.add_argument(
        '--out-filename',
        default='tmp/combined.mp4',
        help='output filename')
    parser.add_argument(
        '--predict-stepsize',
        default=8,
        type=int,
        help='give out a prediction per n frames')
    parser.add_argument(
        '--output-stepsize',
        default=4,
        type=int,
        help=('show one frame per n frames in the demo, we should have: '
              'predict_stepsize % output_stepsize == 0'))
    parser.add_argument(
        '--output-fps',
        default=6,
        type=int,
        help='the fps of demo video output')

    args = parser.parse_args()
    return args

#该函数从给定的视频文件路径中提取帧，并将每一帧保存为图像文件，同时
#返回帧的路径列表和帧数据列表。
def frame_extraction(video_path):
    """Extract frames given video_path.

    Args:
        video_path (str): The video_path.
    """
    # Load the video, 生成的文件名：./tmp/video_name
    target_dir = osp.join('/tmp', osp.basename(osp.splitext(video_path)[0]))
    os.makedirs(target_dir, exist_ok=True) #创建目录
    # Should be able to handle videos up to several hours
    frame_tmpl = osp.join(target_dir, 'img_{:06d}.jpg') #模板
    vid = cv2.VideoCapture(video_path) #逐帧读取视频内容
    frames = [] #存储视频帧数据
    frame_paths = [] #存储每帧对应的图像文件路径
    flag, frame = vid.read() #读取下一帧(flag表示是否读取，frame是帧)
    cnt = 0 #帧计数器
    while flag:
        print("\r frame_extraction: "+str(cnt), end="") #在控制台实时显示当前的帧提取进度
        frames.append(frame)
        frame_path = frame_tmpl.format(cnt + 1) #生成当前帧对应的图像文件名
        frame_paths.append(frame_path)
        cv2.imwrite(frame_path, frame) #把当前帧保存为 JPEG 图像文件
        cnt += 1
        flag, frame = vid.read()
    return frame_paths, frames

#该函数通过加载目标检测模型，对输入帧进行人体检测，并返回每帧中检测到的人体边界框及其置信度
#分数。该函数适用于基于 COCO 数据集训练的检测模型
def detection_inference(args, frame_paths):
    global det_model
    """Detect human boxes given frame paths.

    Args:
        args (argparse.Namespace): The arguments.
        frame_paths (list[str]): The paths of frames to do detection inference.

    Returns:
        list[np.ndarray]: The human detection results.
    """
    
    model = False
    if det_model:
        model = det_model
    else:
        det_model = init_detector(args.det_config, args.det_checkpoint, args.device)
        model = det_model
        
    assert model.CLASSES[0] == 'person', ('We require you to use a detector '
                                          'trained on COCO')
    results = []
    print('Performing Human Detection for each frame')
    prog_bar = mmcv.ProgressBar(len(frame_paths))
    for frame_path in frame_paths:
        result = inference_detector(model, frame_path)
        # We only keep human detections with score larger than det_score_thr
        result = result[0][result[0][:, 4] >= args.det_score_thr]
        results.append(result)
        prog_bar.update()
    return results

#该函数通过加载骨骼检测模型，对输入帧中的人体检测框进行骨骼关键点检测，并返回每帧的骨骼检测结果。该检测
#结果是[人数，关键点数量，3]，3表示 [x, y, 置信度]
# 结果：
# [
#     # 时间戳1的结果
#     [
#         {
#             'bbox': [x1, y1, x2, y2, 置信度],  # 边界框
#             'keypoints': array([  # 17个关键点
#                 [x1, y1, 置信度],  # 关键点1（如鼻子）
#                 [x2, y2, 置信度],  # 关键点2（如左眼）
#                 ...
#             ])
#         }
#     ],
#     # 时间戳2的结果
#     [...],
#     ...
# ]
def detect_skeleton(w_ratio, h_ratio,args,frames,center_frames,timestamps, human_detections):
   global skeleton_model
   global skeleton_dataset
   global skeleton_dataset_info
   predictions = []
   h, w, _ = frames[0].shape
   scale_ratio = np.array([w_ratio, h_ratio, w_ratio, h_ratio])

   #args.pose_config：姿态估计模型配置文件；args.pose_checkpoint：姿态估计模型权重文件
   pose_model = init_pose_model( 
    args.pose_config, args.pose_checkpoint, device=args.device.lower())
   skeleton_model = pose_model
   assert len(timestamps) == len(human_detections)
   prog_bar = mmcv.ProgressBar(len(timestamps))
   for timestamp, proposal,frame in zip(timestamps, human_detections,center_frames):
        if proposal.shape[0] == 0:
            predictions.append(None)
            continue
        # print("proposal:",proposal)
        box = proposal.data.cpu().numpy()
        box = (box / scale_ratio).astype(np.int64)
        box = box[0] #提取第一个检测框（假设只有一个人）
        # print("ske box:",box)
        # st, ed = tuple(box[:2]), tuple(box[2:])
        bbox = (box[0],box[1],box[2],box[3])  
              
        dataset = pose_model.cfg.data['test']['type']
        skeleton_dataset = dataset
        dataset_info = pose_model.cfg.data['test'].get('dataset_info', None)
        if dataset_info is None:
            warnings.warn(
                'Please set `dataset_info` in the config.'
                'Check https://github.com/open-mmlab/mmpose/pull/663 for details.',
                DeprecationWarning)
        else:
            dataset_info = DatasetInfo(dataset_info)
        skeleton_dataset_info = dataset_info
        # print('dataset_info',dataset_info)
        # optional
        return_heatmap = False
        # e.g. use ('backbone', ) to return backbone feature
        output_layer_names = None
        pose_results, returned_outputs = inference_top_down_pose_model(
            pose_model,
            frame,
            [{'bbox':bbox}],
            bbox_thr=None,
            format='xyxy', # - `xyxy` means (left, top, right, bottom),
            dataset=dataset,
            dataset_info=dataset_info,
            return_heatmap=return_heatmap,
            outputs=output_layer_names)
        # print("pose_results:",pose_results)
        predictions.append(pose_results)
   return predictions
  
#从指定的文件路径加载标签映射表，并将其解析为一个字典，键为整数，值为对应的标签名称
def load_label_map(file_path):
    """Load Label Map.

    Args:
        file_path (str): The file path of label map.

    Returns:
        dict: The label map (int -> label name).
    """
    lines = open(file_path).readlines()
    lines = [x.strip().split(': ') for x in lines]
    return {int(x[0]): x[1] for x in lines}


def abbrev(name):
    """Get the abbreviation of label name:

    'take (an object) from (a person)' -> 'take ... from ...'
    """
    while name.find('(') != -1:
        st, ed = name.find('('), name.find(')')
        name = name[:st] + '...' + name[ed + 1:]
    return name

#将人体检测结果、动作预测结果、骨骼预测结果以及图像的高度和宽度打包成一个元组，返回每个人体
#检测框的位置信息、标签名称和标签分数
# 结果：
#  # 时间戳1的结果
#     [
#         (
#             # 边界框坐标（归一化）
#             array([0.2, 0.3, 0.4, 0.6]),
#             # 动作类别列表
#             ["walking", "talking", "hand_wave"],
#             # 动作置信度列表
#             [0.9, 0.7, 0.5],
#             # 骨骼关键点坐标（17个点，每个点[x,y,置信度]）
#             array([
#                 [100, 200, 0.9],  # 鼻子
#                 [90, 180, 0.8],   # 左眼
#                 ...
#             ])
#         )
#     ],
#     # 时间戳2的结果
#     [
#         (
#             array([0.21, 0.32, 0.41, 0.61]),
#             ["standing", "writing"],
#             [0.95, 0.8],
#             array([...])
#         )
#     ]
# ]
def pack_resultslowfast(human_detection, result, sleletons_predictions, img_h, img_w):
    """Short summary.

    Args:
        human_detection (np.ndarray): Human detection result.
        result (type): The predicted label of each human proposal.
        img_h (int): The image height.
        img_w (int): The image width.

    Returns:
        tuple: Tuple of human proposal, label name and label score.
    """
    human_detection[:, 0::2] /= img_w
    human_detection[:, 1::2] /= img_h
    results = []
    if result is None:
        return None
    for prop, res in zip(human_detection, result):#result：一个人动作预测的结果列表
        res.sort(key=lambda x: -x[1])
        skeleton_res = behavior_(sleletons_predictions)
        process_skeleton_res(res,skeleton_res) # 添加上骨骼的标签显示
        # print('behavior_:',)
        results.append(
            (prop.data.cpu().numpy(), [x[0] for x in res], [x[1]
                                                            for x in res],sleletons_predictions))
        slow_fast_behavior(res)
    return results

def process_skeleton_res(res,skeleton_res):
    for sr in skeleton_res:
        if skeleton_res[sr] > 0:
            res.append([sr,0.5,0])
        
#通过分析人体骨骼关键点数据列表（pose_results）来识别教师的课堂行为，并更新全局的行为统计结果  1111111111111111111111
def behavior_(pose_results):
    global analyze_result,analyze_time_line,skeletonChecker
    write_blackboard = 0
    look_at_student=0
    explanation=0
    look_at_computer=0
    sit_times=0
    look_at_blackboard=0
    hand_point_front=0
    hand_point_down = 0
    hand_point_up = 0
    hand_point_horizon = 0
    
    try:
        res = pose_results[0]['keypoints']
        skeletonChecker.addCurrentFrame(res) # 必须先存储骨骼关键点
        # ===== 调用手部姿态检测 =====
        hand_gestures = skeletonChecker.checkHandGesture()#手部姿态判断逻辑的函数
        # 更新手势的全局统计
        for gesture_type, count in hand_gestures.items():
            analyze_result[gesture_type] += count
        #写/讲解板书  
        write_blackboard = skeletonChecker.checkWriteBlackboard()
        analyze_result['write_blackboard'] += write_blackboard  
        
        #看学生  
        look_at_student = skeletonChecker.checkWriteBlackboard()
        analyze_result['look_at_student'] += look_at_student  

        # 带肢体动作讲解
        explanation = skeletonChecker.checkExplain()
        analyze_result['explanation'] += explanation    

        # 讲台低头（看电脑）
        look_at_computer = skeletonChecker.checkLookAtComputer()
        analyze_result['look_at_computer'] += look_at_computer  
    
        # 看黑板或看投影
        look_at_blackboard = skeletonChecker.checkLookAtToBlackboard()
        analyze_result['look_at_blackboard'] += look_at_blackboard  
        
        # 在讲台柜中
        sit_times = skeletonChecker.checkInCounter(res)
        analyze_result['sit_times'] += sit_times 
        
        # # 向前指
        # hand_point_front = skeletonChecker.checkPointFront()
        # analyze_result['hand_point_front'] += hand_point_front
        
    except Exception as e:
        print("从Pose数据映射动作失败 ！ ")
        print(e)
    
    
    return {'写/讲解板书 ':write_blackboard,
            '看学生 ':look_at_student,
            '带肢体动作讲解':explanation,
            '讲台低头（看电脑）':look_at_computer,
            '在讲台柜中':sit_times,
            '向前指': hand_point_front,
            '手向下': hand_point_down,
            '手向上': hand_point_up,
            '手水平': hand_point_horizon
            }
  
    
    
    
def triangle_area(p1,p2,p3):#三角形计算面积
    vec1=np.array(p2)-np.array(p1)
    vec2=np.array(p3)-np.array(p1)
    area=np.abs(vec1[0]*vec2[1]-vec1[1]*vec2[0])
    return area/2.0


# def pack_result(human_detection, result, img_h, img_w):
#     """Short summary.

#     Args:
#         human_detection (np.ndarray): Human detection result.
#         result (type): The predicted label of each human proposal.
#         img_h (int): The image height.
#         img_w (int): The image width.

#     Returns:
#         tuple: Tuple of human proposal, label name and label score.
#     """
#     human_detection[:, 0::2] /= img_w
#     human_detection[:, 1::2] /= img_h
#     results = []
#     if result is None:
#         return None
#     for prop, res in zip(human_detection, result):
#         res.sort(key=lambda x: -x[1])
#         results.append(
#             (prop.data.cpu().numpy(), [x[0] for x in res], [x[1]
#                                                             for x in res]))
#         custom_behavior(res)
#     return results


def action_prediction(timestamps,human_detections,config,args,clip_len,frame_interval,frames,window_size,img_norm_cfg,new_h,new_w,label_map):
    # region 1
    global action_model
    model = False
    if action_model:
        model = action_model
    else:    
        action_model = build_detector(config.model, test_cfg=config.get('test_cfg'))
        model = action_model
        load_checkpoint(model, args.checkpoint, map_location='cpu')
        model.to(args.device)
        model.eval()

    predictions = []

    print('Performing SpatioTemporal Action Detection for each clip')
    assert len(timestamps) == len(human_detections)
    prog_bar = mmcv.ProgressBar(len(timestamps))
    for timestamp, proposal in zip(timestamps, human_detections):
        if proposal.shape[0] == 0:
            predictions.append(None)
            continue

        start_frame = timestamp - (clip_len // 2 - 1) * frame_interval
        frame_inds = start_frame + np.arange(0, window_size, frame_interval)
        frame_inds = list(frame_inds - 1)
        imgs = [frames[ind].astype(np.float32) for ind in frame_inds]
        _ = [mmcv.imnormalize_(img, **img_norm_cfg) for img in imgs]
        # THWC -> CTHW -> 1CTHW
        input_array = np.stack(imgs).transpose((3, 0, 1, 2))[np.newaxis]
        input_tensor = torch.from_numpy(input_array).to(args.device)

        with torch.no_grad():
            result = model(
                return_loss=False,
                img=[input_tensor],
                img_metas=[[dict(img_shape=(new_h, new_w))]],
                proposals=[[proposal]])
            result = result[0]
            prediction = []
            # N proposals
            for i in range(proposal.shape[0]):
                prediction.append([])
            # Perform action score thr
            for i in range(len(result)):
                if i + 1 not in label_map:
                    continue
                for j in range(proposal.shape[0]):
                    if result[i][j, 4] > args.action_score_thr:
                        prediction[j].append((label_map[i + 1], result[i][j,4],i+1))
            predictions.append(prediction)
        prog_bar.update()
    # end region 1
    return predictions

#用 SlowFast 模型进行时空动作检测，预测每个检测框中的动作类别 返回结果：[时间戳1[(动作名称1, 置信度1, 类别索引1), (动作名称2, 置信度2, 类别索引2), ...],...]
def action_predictionslowfast(timestamps,human_detections,config,args,clip_len,frame_interval,frames,window_size,img_norm_cfg,new_h,new_w,label_map):
    # region 1
    global action_model_slowfast
    model = False
    if action_model_slowfast:
        model = action_model_slowfast
    else:    
        action_model = build_detector(config.model, test_cfg=config.get('test_cfg'))
        model = action_model
        load_checkpoint(model, args.checkpointslowfast, map_location='cpu')
        model.to(args.device)
        model.eval()

    predictions = []

    print('Performing SpatioTemporal Action Detection for each clip')
    assert len(timestamps) == len(human_detections)
    prog_bar = mmcv.ProgressBar(len(timestamps))
    for timestamp, proposal in zip(timestamps, human_detections):
        if proposal.shape[0] == 0:
            predictions.append(None)
            continue

        start_frame = timestamp - (clip_len // 2 - 1) * frame_interval
        frame_inds = start_frame + np.arange(0, window_size, frame_interval)
        frame_inds = list(frame_inds - 1)
        imgs = [frames[ind].astype(np.float32) for ind in frame_inds]
        _ = [mmcv.imnormalize_(img, **img_norm_cfg) for img in imgs]
        # THWC -> CTHW -> 1CTHW
        input_array = np.stack(imgs).transpose((3, 0, 1, 2))[np.newaxis]
        input_tensor = torch.from_numpy(input_array).to(args.device)

        with torch.no_grad():
            result = model(
                return_loss=False,
                img=[input_tensor],
                img_metas=[[dict(img_shape=(new_h, new_w))]],
                proposals=[[proposal]])
            result = result[0]
            prediction = []
            # N proposals
            for i in range(proposal.shape[0]):
                prediction.append([])
            # Perform action score thr
            for i in range(len(result)):
                if i + 1 not in label_map:
                    continue
                for j in range(proposal.shape[0]):
                    if result[i][j, 4] > args.action_score_thr:
                        prediction[j].append((label_map[i + 1], result[i][j,4],i+1))
            predictions.append(prediction)
        prog_bar.update()
    # end region 1
    return predictions

#该函数 process_videoslowfast 的作用是对输入的视频进行处理，主要包括以下步骤：
# 视频帧提取：从视频中提取帧并调整尺寸。
# 人体检测：对每一帧进行人体检测，获取检测框。
# 动作预测：使用 SlowFast 模型进行时空动作检测，预测每个检测框中的动作类别。
# 骨骼检测：对检测到的人体进行骨骼关键点检测。
# 结果整合：将人体检测结果、动作预测结果和骨骼检测结果整合到一起。
# 可视化（可选）：将检测结果绘制到视频帧上，并生成输出视频。
def process_videoslowfast(video_path,video_out_filename,do_vis):
    global frame_paths, original_frames #存储处理后帧的图片的路径列表和原始帧数据列表
    args = parse_args()
    
    if not original_frames: #检查如果不为空
        frame_paths, original_frames = frame_extraction(video_path)

    frame_paths, original_frames = frame_extraction(video_path)
    num_frame = len(frame_paths) #帧的长度
    h, w, _ = original_frames[0].shape

    # 等比例缩放，确保短边长度为 256 像素
    new_w, new_h = mmcv.rescale_size((w, h), (256, np.Inf))
    frames = [mmcv.imresize(img, (new_w, new_h)) for img in original_frames]
    w_ratio, h_ratio = new_w / w, new_h / h

    # Get clip_len, frame_interval and calculate center index of each clip
    config = mmcv.Config.fromfile(args.configslowfast) #从指定路径加载 SlowFast 模型的配置文件
    config.merge_from_dict(args.cfg_optionsslowfast) #使用命令行参数动态覆盖配置文件中的默认值
    val_pipeline = config.data.val.pipeline #提取验证数据处理流程

    #从验证数据处理流程中找出用于 AVA 数据集的帧采样器配置
    sampler = [x for x in val_pipeline if x['type'] == 'SampleAVAFrames'][0]
    clip_len, frame_interval = sampler['clip_len'], sampler['frame_interval']
    window_size = clip_len * frame_interval
    #确保clip_len是偶数，以便后续能均等地划分时间窗口
    assert clip_len % 2 == 0, 'We would like to have an even clip_len'
    # 生成一系列时间戳（从长视频里截片段），用于确定模型预测的起始帧位置
    timestamps = np.arange(window_size // 2, num_frame + 1 - window_size // 2,
                           args.predict_stepsize)

    # 从指定文件加载标签映射字典(/home/user3/mmaction2_YF_2/tools/data/ava/label_map.txt)
    label_map = load_label_map(args.label_mapslowfast)

    # 检查配置文件中是否存在data.train.custom_classes字段。
    # 遍历自定义类别列表，从原始标签映射中提取对应类别。
    # 使用新的 ID（从 1 开始）重新编号，生成新的标签映射。
    try:
        if config['data']['train']['custom_classes'] is not None:
            label_map = {
                id + 1: label_map[cls]
                for id, cls in enumerate(config['data']['train']
                                         ['custom_classes'])
            }
    except KeyError:
        pass

    # Get Human detection results
    center_frames = [frame_paths[ind - 1] for ind in timestamps] #提取出时间戳对应的中心帧路径
    human_detections = detection_inference(args, center_frames) #对每个中心帧进行人体检测，获取边界框信息(列表)
    for i in range(len(human_detections)):
        det = human_detections[i]
        det[:, 0:4:2] *= w_ratio #start:stop:step (x轴)
        det[:, 1:4:2] *= h_ratio #(y轴)
        #将处理后的边界框坐标从 NumPy 数组转换为 PyTorch 张量，并将其移动到指定的计算设备
        human_detections[i] = torch.from_numpy(det[:, :4]).to(args.device)

    # Get img_norm_cfg
    img_norm_cfg = config['img_norm_cfg'] #提取图像归一化的配置
    if 'to_rgb' not in img_norm_cfg and 'to_bgr' in img_norm_cfg: #rgb、bgr是读取图像的顺序
        to_bgr = img_norm_cfg.pop('to_bgr') #删除bgr
        img_norm_cfg['to_rgb'] = to_bgr #添加rgb
    img_norm_cfg['mean'] = np.array(img_norm_cfg['mean']) #将均值转换为 NumPy 数组
    img_norm_cfg['std'] = np.array(img_norm_cfg['std'])

    # Build STDET model
    try:
       #保留所有的预测动作，保证不同动作类别的边界框数量相同，便于后续处理
        config['model']['test_cfg']['rcnn']['action_thr'] = .0
    except KeyError:
        pass

    config.model.backbone.pretrained = None #不加载预训练模型(已有训练好的模型)

    #使用 SlowFast 模型对每个检测到的人体进行动作分类，输出动作预测结果（列表）
    predictions = action_predictionslowfast(timestamps,human_detections,config,args,clip_len,frame_interval,frames,window_size,img_norm_cfg,new_h,new_w,label_map)
    
    print('Performing Skeleton Detection for each human proposal')
    sleletons_predictions = []
    # 对每个检测到的人体进行骨骼关键点检测，得到一个含有每个人体骨骼关键点的列表
    sleletons_predictions = detect_skeleton(w_ratio, h_ratio,args,frames,center_frames,timestamps, human_detections)

    print('human_detections lens :+++++++++' , len(human_detections))
    print('action predictions lens :+++++++++' , len(predictions))
    print('sleletons_predictions lens :+++++++++' , len(sleletons_predictions))
    

    results = []
    for human_detection, prediction,sleletons_prediction in zip(human_detections, predictions,sleletons_predictions):
        results.append(pack_resultslowfast(human_detection, prediction,sleletons_prediction, new_h, new_w))
    
    #将稀疏的时间戳（预测帧）扩展为密集的时间戳（显示帧）
    def dense_timestamps(timestamps, n):
        """Make it nx frames."""
        old_frame_interval = (timestamps[1] - timestamps[0])
        start = timestamps[0] - old_frame_interval / n * (n - 1) / 2
        new_frame_inds = np.arange(
            len(timestamps) * n) * old_frame_interval / n + start
        return new_frame_inds.astype(np.int64)
#args.predict_stepsize：每隔多少帧进行一次动作预测；args.output_stepsize：每隔多少帧显示一个预测结果，要求args.predict_stepsize % args.output_stepsize == 0
    dense_n = int(args.predict_stepsize / args.output_stepsize)#每次动作预测，会有dense_n次预测结果展示
    frames = [
        cv2.imread(frame_paths[i - 1]) #从文件路径读取图像并返回 NumPy 数组
        for i in dense_timestamps(timestamps, dense_n)
    ]
    
    if do_vis: #函数传入的值为true
        print('Performing visualization')
        #添加了可视化标记的帧列表
        vis_frames = visualize(frames, results)
        #将图像序列转换为视频剪辑
        vid = mpy.ImageSequenceClip([x[:, :, ::-1] for x in vis_frames],
                                    fps=args.output_fps)#设置输出视频的帧率（6FPS）
        vid.write_videofile(video_out_filename) #保存视频文件，自动根据文件名扩展名确定

    tmp_frame_dir = osp.dirname(frame_paths[0]) #获取临时帧目录路径
    shutil.rmtree(tmp_frame_dir) #递归删除目录及其所有内容

# def custom_behavior(res):
#     global analyze_result,analyze_time_line
#     for x in res:
#         action_type = x[2]
#         # if action_type == 14: #走动
#         #     analyze_result['walk_times'] += 1
#         # if action_type == 12: #站立
#         #     analyze_result['stand_times'] += 1
#         # if action_type == 11: #坐着
#         #     analyze_result['sit_times'] += 1   
#         #     analyze_result['sit_down'] += 1   
#         # if action_type == 17: #拿
#         #     analyze_result['take_times'] += 1 
              
#         if action_type == 9 : #比划
#             analyze_result['hand_wave'] += 1   
#         if action_type == 14: #看学生
#             analyze_result['look_at_student'] += 1  
#         if action_type == 18: #写板书
#             analyze_result['write_blackboard'] += 1  
#         if action_type == 19 : #讲解
#             analyze_result['explanation'] += 1


def slow_fast_behavior(res):
    global analyze_result,analyze_time_line
    # 坐站比统计
    def addItem(array,item):
        if item in array:
            pass
        else:
            array.append(item)
    item = []
    for x in res:
        action_type = x[2]
        if action_type == 14: #走动
            analyze_result['walk_times'] += 1
            addItem(item,'走动')
        if action_type == 12: #站立
            analyze_result['stand_times'] += 1
            addItem(item,'站')
        if action_type == 11: #坐着
            analyze_result['sit_times'] += 1   
            addItem(item,'静止')
        if action_type == 17: #拿
            analyze_result['take_times'] += 1 
        if action_type == 69 or action_type == 68: #挥手
            analyze_result['hand_wave'] += 1   
        if action_type == 80: #看学生
            addItem(item,'看学生')
            analyze_result['look_at_student'] += 1  
        if action_type == 34 or action_type == 63: #写板书
            addItem(item,'写板书')
            analyze_result['write_blackboard'] += 1  
        if action_type == 79 or action_type == 15: #讲解
            addItem(item,'讲解')
            analyze_result['explanation'] += 1
        if action_type == 59: #看黑板
            addItem(item,'看黑板')
            analyze_result['look_at_blackboard'] += 1
    analyze_time_line.append(item)
            

# def process_video(video_path,video_out_filename,do_vis):
#     global frame_paths, original_frames 
#     args = parse_args()
    
#     if not original_frames:
#         frame_paths, original_frames = frame_extraction(video_path)
#     num_frame = len(frame_paths)
#     h, w, _ = original_frames[0].shape

#     # resize frames to shortside 256
#     new_w, new_h = mmcv.rescale_size((w, h), (256, np.Inf))
#     frames = [mmcv.imresize(img, (new_w, new_h)) for img in original_frames]
#     w_ratio, h_ratio = new_w / w, new_h / h

#     # Get clip_len, frame_interval and calculate center index of each clip
#     config = mmcv.Config.fromfile(args.config)
#     config.merge_from_dict(args.cfg_options)
#     val_pipeline = config.data.val.pipeline

#     sampler = [x for x in val_pipeline if x['type'] == 'SampleAVAFrames'][0]
#     clip_len, frame_interval = sampler['clip_len'], sampler['frame_interval']
#     window_size = clip_len * frame_interval
#     assert clip_len % 2 == 0, 'We would like to have an even clip_len'
#     # Note that it's 1 based here
#     timestamps = np.arange(window_size // 2, num_frame + 1 - window_size // 2,
#                            args.predict_stepsize)

#     # Load label_map
#     label_map = load_label_map(args.label_map)
#     try:
#         if config['data']['train']['custom_classes'] is not None:
#             label_map = {
#                 id + 1: label_map[cls]
#                 for id, cls in enumerate(config['data']['train']
#                                          ['custom_classes'])
#             }
#     except KeyError:
#         pass

#     # Get Human detection results
#     center_frames = [frame_paths[ind - 1] for ind in timestamps]
#     human_detections = detection_inference(args, center_frames)
#     for i in range(len(human_detections)):
#         det = human_detections[i]
#         det[:, 0:4:2] *= w_ratio
#         det[:, 1:4:2] *= h_ratio
#         human_detections[i] = torch.from_numpy(det[:, :4]).to(args.device)

#     # Get img_norm_cfg
#     img_norm_cfg = config['img_norm_cfg']
#     if 'to_rgb' not in img_norm_cfg and 'to_bgr' in img_norm_cfg:
#         to_bgr = img_norm_cfg.pop('to_bgr')
#         img_norm_cfg['to_rgb'] = to_bgr
#     img_norm_cfg['mean'] = np.array(img_norm_cfg['mean'])
#     img_norm_cfg['std'] = np.array(img_norm_cfg['std'])

#     # Build STDET model
#     try:
#         # In our spatiotemporal detection demo, different actions should have
#         # the same number of bboxes.
#         config['model']['test_cfg']['rcnn']['action_thr'] = .0
#     except KeyError:
#         pass

#     config.model.backbone.pretrained = None
    
#     predictions = action_prediction(timestamps,human_detections,config,args,clip_len,frame_interval,frames,window_size,img_norm_cfg,new_h,new_w,label_map)


#     results = []
#     for human_detection, prediction in zip(human_detections, predictions):
#         results.append(pack_result(human_detection, prediction, new_h, new_w))

#     if do_vis:
#         def dense_timestamps(timestamps, n):
#             """Make it nx frames."""
#             old_frame_interval = (timestamps[1] - timestamps[0])
#             start = timestamps[0] - old_frame_interval / n * (n - 1) / 2
#             new_frame_inds = np.arange(
#                 len(timestamps) * n) * old_frame_interval / n + start
#             return new_frame_inds.astype(np.int64)

#         dense_n = int(args.predict_stepsize / args.output_stepsize)
#         frames = [
#             cv2.imread(frame_paths[i - 1])
#             for i in dense_timestamps(timestamps, dense_n)
#         ]
#         print('Performing customer visualization')
#         vis_frames = visualize_customer(frames, results)
#         vid = mpy.ImageSequenceClip([x[:, :, ::-1] for x in vis_frames],
#                                     fps=args.output_fps)
#         vid.write_videofile(video_out_filename)

#     tmp_frame_dir = osp.dirname(frame_paths[0])
#     shutil.rmtree(tmp_frame_dir)

# 新增全局变量
video_id = None

def main():
    global analyze_result,analyze_time_line #动作统计数据、时间线数据
    args = parse_args()
    course_id = args.course_id
    print("course_id: ++++++++++++" , course_id)
    video_path = args.video #输入视频路径
    out_filename = args.out_filename #输出视频路路径
    vp = VideoProcess(video_path,out_filename) #创建VideoProcess对象，处理视频的分割和合并
    cuted_files_array = vp.cuted_files_array #存储分割后的视频文件路径的列表
    processed_files_array = vp.processed_files_array #存储处理后的视频文件路径的列表
    for cut,pro in zip(cuted_files_array,processed_files_array):
        print("处理的文件",cut,pro)
        process_videoslowfast(cut,pro,True)
        # process_video(cut,pro,False)
    vp.combine()
    #计算评分
    total_frames = len(analyze_time_line) if analyze_time_line else 100  # 若时间线为空，则默认100帧
    teaching_score, score_explanation = calculate_teaching_score(analyze_result, total_frames)
    result_json_path = args.result_json #输出结果的 JSON 文件路径“tmp”
    # 保存结果
    with open(result_json_path, 'w') as f:
        json.dump({
            'video_id': args.video_id,
            'analyze_result': analyze_result,
            'teaching_score': teaching_score,
            'score_explanation': score_explanation,
            # 'analyze_time_line': analyze_time_line,
            # 'video_duration': duration,
            # 'processed_frames': len(analyze_time_line)
        }, f, indent=2)
    
if __name__ == '__main__':
    main()
    print(analyze_result)
     
#思路：分割视频->分成视频帧->时间戳->人体检测->动作预测->骨骼检测->结果整合->可视化
'''
python /home/user3/mmaction2_YF_2/demo/算法文件.py --video demo.mp4 --out-filename /home/user3/mmaction2_YF_2/demo/processed_result/1.mp4 --result-json /home/user3/mmaction2_YF_2/demo/processed_result/1.json
'''
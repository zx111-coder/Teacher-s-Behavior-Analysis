import axios from 'axios';

const instance=axios.create({
    timeout:5000,
})
//浏览器状态码
const errorHandle=(status,info)=>{
    switch(status){
        case 400:
            console.log("请求错误");
            break;
        case 401:
            console.log("服务器认证失败");
            break;
        case 403:
            console.log("服务器拒绝访问");
            break;
        case 404:
            console.log("请求地址出错");
            break;
        case 408:
            console.log("请求超时");
            break;
        case 500:
            console.log("服务器内部错误");
            break;
        case 501:
            console.log("服务未实现");
            break;
        case 502:
            console.log("网关错误");
            break;
        case 503:
            console.log("服务不可用");
            break;
        case 504:
            console.log("网关超时");
            break;
        case 505:
            console.log("HTTP版本不受支持");
            break;
        default:
            console.log(info);
    }
}

//请求拦截器
instance.interceptors.request.use(
    config=>{
        //请求前的处理
        return config;
    },
    error=>{
        return Promise.reject(error);
    }
)
//响应拦截器
instance.interceptors.response.use(
    response=>{
        //响应后的处理
        return response;
    },
    error=>{
        errorHandle(error.response.status,error.response.info);
        console.log("error响应错误:",error);
        return Promise.reject(error);
    }
)
export default instance;
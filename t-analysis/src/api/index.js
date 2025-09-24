import path from './path.js'
import axios from '../utils/request.js'
const api={
    async setUser(data){
        const res=await axios.post(path.baseurl+'/users',data);
        return res;
    },
    async getUser(){
        const res=await axios.get(path.baseurl+'/users');
        return res;
    }
}
export default api;
import axios from "axios";

axios.defaults.headers.common["Accept-Language"] =
    localStorage.getItem("nestorria-lang") ?? "en";
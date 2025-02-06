package com.trip.app.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @RequestMapping("/login" )
    public String login(String acc,String pwd){
        System.out.println(1);
        return "ok";
    }
}

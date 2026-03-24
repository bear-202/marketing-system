package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;


@RestController
@RequestMapping("/follow")
public class FollowController {
     @Resource
    private IFollowService iFollowService;

     @GetMapping("/or/not/{followId}")
    public Result isFollow(@PathVariable("followId") Long followId){
         return iFollowService.isFollow(followId);
     }

     @PutMapping("/{followId}/{isFollow}")
    public Result follow(@PathVariable("followId") Long followId ,@PathVariable("isFollow") boolean isFollow){
         return iFollowService.follow(followId,isFollow);
     }

     @GetMapping("/common/{followId}")
    public Result commonFollow(@PathVariable("followId") Long followId){
         return iFollowService.commonFollow(followId);
     }
}

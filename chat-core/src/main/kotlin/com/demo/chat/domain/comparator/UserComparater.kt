package com.demo.chat.domain.comparator

import com.demo.chat.domain.User


class UserComparater<T>  : Comparator<User<T>> {
    override fun compare(o1: User<T>, o2: User<T>): Int {
        return o1.handle.toLowerCase().compareTo(o2.handle.toLowerCase())
    }
}
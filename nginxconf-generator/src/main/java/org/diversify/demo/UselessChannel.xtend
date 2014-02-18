package org.diversify.demo

import org.kevoree.annotation.ChannelType
import org.kevoree.annotation.KevoreeInject
import org.kevoree.annotation.Library
import org.kevoree.api.Callback
import org.kevoree.api.ChannelContext
import org.kevoree.api.ChannelDispatch

@ChannelType
@Library(name = "Java")
public class UselessChannel implements ChannelDispatch {

    @KevoreeInject
    ChannelContext channelContext;
	
	def override  ^dispatch(Object arg0, Callback arg1) {

	}

}

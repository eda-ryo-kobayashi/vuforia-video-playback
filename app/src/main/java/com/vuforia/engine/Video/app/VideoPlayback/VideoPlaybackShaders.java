/*===============================================================================
Copyright (c) 2016,2018 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.Video.app.VideoPlayback;

public class VideoPlaybackShaders {

    public static final String VIDEO_PLAYBACK_VERTEX_SHADER = " \n"
            + "attribute vec4 vertexPosition; \n"
            + "attribute vec2 vertexTexCoord; \n"
            + "varying vec2 texCoord; \n"
            + "uniform mat4 modelViewProjectionMatrix; \n"
            + "\n"
            + "void main() \n"
            + "{ \n"
            + "   gl_Position = modelViewProjectionMatrix * vertexPosition; \n"
            + "   texCoord = vertexTexCoord; \n"
            + "} \n";

}

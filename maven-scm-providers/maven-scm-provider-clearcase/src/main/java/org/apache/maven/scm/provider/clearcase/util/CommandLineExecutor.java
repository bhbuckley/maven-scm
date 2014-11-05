package org.apache.maven.scm.provider.clearcase.util;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/*
 * Component interface for a CommandLine executor service
 */
public interface CommandLineExecutor {
	
    /**
     * Role used to register component implementations with the container.
     */
    String ROLE = CommandLineExecutor.class.getName ();

    /**
     * Execute a CommandLine
     * @param cl
     * 			CommandLine to execute
     * @param systemOut
     * 			Consumer for System.out
     * @param systemErr
     * 			Consumer for System.err
     * @return
     * 			Exit code of the process represented by the CommandLine
     * @throws CommandLineException 
     * 			Error occurred during execution of the CommandLine
     */
	int executeCommandLine( Commandline cl, StreamConsumer systemOut, StreamConsumer systemErr ) throws CommandLineException;
}

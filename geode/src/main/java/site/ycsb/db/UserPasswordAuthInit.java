package site.ycsb.db;

import java.util.Properties;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.security.AuthInitialize;
import org.apache.geode.LogWriter;
import org.apache.geode.security.AuthenticationFailedException;

import site.ycsb.measurements.Measurements;


/**
 * Apache Geode client for the YCSB benchmark.
//https://gemfire.dev/blog/security-manager-basics-authentication-and-authorization/
*/
public class UserPasswordAuthInit implements AuthInitialize { 
  @Override
  public void init(LogWriter systemLogger,
                  LogWriter securityLogger) throws AuthenticationFailedException {
  
  }

  @Override
  public void close() {
  
  }

  @Override
  public Properties getCredentials(Properties properties, DistributedMember distributedMember, boolean isPeer) 
  throws AuthenticationFailedException {
    Properties props= Measurements.getProperties();
    System.out.println("## UserPasswordAuthInit security-username:"+ props.getProperty("gfsh-username"));
    properties.setProperty("security-username", props.getProperty("gfsh-username"));
    properties.setProperty("security-password", props.getProperty("gfsh-password"));
    return properties; 
  } 


}
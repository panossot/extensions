package org.jboss.weld.extensions.bean.generic;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.event.Reception;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.ObserverMethod;

import org.jboss.weld.extensions.bean.ForwardingObserverMethod;
import org.jboss.weld.extensions.bean.InjectableMethod;
import org.jboss.weld.extensions.bean.ParameterValueRedefiner;
import org.jboss.weld.extensions.util.Synthetic;

class GenericObserverMethod<T, X> extends ForwardingObserverMethod<T>
{

   private final ObserverMethod<T> originalObserverMethod;
   private final InjectableMethod<X> observerMethod;
   private final BeanManager beanManager;
   private final Type declaringBeanType;
   private final Annotation declaringBeanQualifier;
   private final Set<Annotation> qualifiers;

   GenericObserverMethod(ObserverMethod<T> originalObserverMethod, AnnotatedMethod<X> observerMethod, Annotation declaringBeanQualifier, Set<Annotation> qualifiers, Synthetic.Provider syntheticProvider, BeanManager beanManager)
   {
      this.originalObserverMethod = originalObserverMethod;
      this.observerMethod = new InjectableMethod<X>(observerMethod, null, beanManager);
      this.beanManager = beanManager;
      this.declaringBeanQualifier = syntheticProvider.get(declaringBeanQualifier);
      this.declaringBeanType = originalObserverMethod.getBeanClass();
      this.qualifiers = qualifiers;
   }

   @Override
   protected ObserverMethod<T> delegate()
   {
      return originalObserverMethod;
   }
   
   @Override
   public Set<Annotation> getObservedQualifiers()
   {
      return qualifiers;
   }

   @Override
   public void notify(final T event)
   {
      Bean<?> declaringBean = beanManager.resolve(beanManager.getBeans(declaringBeanType, declaringBeanQualifier));
      final CreationalContext<?> creationalContext = createCreationalContext(declaringBean);
      try
      {
         Object instance = beanManager.getReference(declaringBean, declaringBeanType, creationalContext);
         if (instance == null)
         {
            return;
         }
         observerMethod.invoke(instance, creationalContext, new ParameterValueRedefiner()
         {

            public Object redefineParameterValue(ParameterValue value)
            {
               if (value.getInjectionPoint().getAnnotated().isAnnotationPresent(Observes.class))
               {
                  return event;
               }
               else
               {
                  return value.getDefaultValue(creationalContext);
               }
            }
         });

      }
      finally
      {
         // Generic beans are always dependent scoped
         if (creationalContext != null)
         {
            creationalContext.release();
         }
      }

   }

   private CreationalContext<?> createCreationalContext(Bean<?> declaringBean)
   {
      if (getReception().equals(Reception.ALWAYS))
      {
         return beanManager.createCreationalContext(declaringBean);
      }
      else
      {
         return null;
      }
   }
   
   @Override
   public boolean equals(Object obj)
   {
      if (obj instanceof GenericObserverMethod<?, ?>)
      {
         GenericObserverMethod<?, ?> that = (GenericObserverMethod<?, ?>) obj;
         return this.delegate().equals(that.delegate()) && this.getObservedType().equals(that.getObservedType()) && this.getObservedQualifiers().equals(that.getObservedQualifiers());
      }
      else
      {
         return false;
      }
   }

   @Override
   public int hashCode()
   {
      int hash = 2;
      hash = 31 * hash + this.getObservedType().hashCode();
      hash = 31 * hash + this.getObservedQualifiers().hashCode();
      return hash;
   }

}

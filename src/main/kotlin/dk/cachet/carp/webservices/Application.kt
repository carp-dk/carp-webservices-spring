package dk.cachet.carp.webservices

import org.springframework.beans.BeansException
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Component

@SpringBootApplication
@EnableAsync
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Component
class SpringApplicationContext : ApplicationContextAware {
    companion object {
        private var context: ApplicationContext? = null

        fun <T : Any> getBean(clazz: Class<T>): T {
            return context!!.getBean(clazz)
        }
    }

    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        context = applicationContext
    }
}
